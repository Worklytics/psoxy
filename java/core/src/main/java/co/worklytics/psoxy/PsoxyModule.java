package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.storage.BulkDataSanitizerFactory;
import co.worklytics.psoxy.storage.impl.BulkDataSanitizerFactoryImpl;
import com.avaulta.gateway.pseudonyms.impl.Base64UrlSha256HashPseudonymEncoder;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.JsonSchemaFilterUtils;
import com.avaulta.gateway.rules.ParameterSchemaUtils;
import com.avaulta.gateway.rules.PathTemplateUtils;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.api.client.http.HttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import dagger.Module;
import dagger.Provides;
import lombok.extern.java.Log;

import javax.crypto.spec.SecretKeySpec;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * provides implementations for platform-independent dependencies of 'core' module
 */
@Log
@Module
public class PsoxyModule {


    @Provides
    @Singleton
        //should be thread-safe
    ObjectMapper providesObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    @Provides @Singleton
    @Named("ForYAML")
    ObjectMapper providesYAMLObjectMapper() {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Provides @Singleton
    Configuration providesJSONConfiguration(JacksonJsonProvider jacksonJsonProvider,
                                            JacksonMappingProvider jacksonMappingProvider) {
        //jackson here because it's our common JSON stack, but adds dependency beyond the one pkg'd
        // with JsonPath.
        return Configuration.defaultConfiguration()
                .jsonProvider(jacksonJsonProvider)
                .mappingProvider(jacksonMappingProvider);
    }

    @Provides @Singleton
    JacksonJsonProvider jacksonJsonProvider(ObjectMapper objectMapper) {
        return new JacksonJsonProvider(objectMapper);
    }

    @Provides @Singleton
    JacksonMappingProvider jacksonMappingProvider(ObjectMapper objectMapper) {
        return new JacksonMappingProvider(objectMapper);
    }

    @Provides @Singleton
    JsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    @Provides @Singleton
    static Logger logger() {
        return Logger.getLogger(PsoxyModule.class.getCanonicalName());
    }

    @Provides @Singleton
    static SourceAuthStrategy sourceAuthStrategy(ConfigService configService, Set<SourceAuthStrategy> sourceAuthStrategies) {
        String identifier = configService.getConfigPropertyOrError(ApiModeConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER);
        return sourceAuthStrategies
                .stream()
                .filter(impl -> Objects.equals(identifier, impl.getConfigIdentifier()))
                .findFirst()
                .orElseThrow(() -> new Error("No SourceAuthStrategy impl matching configured identifier: " + identifier));
    }

    @Provides
    static OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder tokenRequestPayloadBuilder(ConfigService configService,
                                                                                              Set<OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder> payloadBuilders) {
        // Final instantiation of configs are per-function. Grant type is dependent of type of auth
        // strategy so might not exist for certain functions.
        // If it is mis-configured will throw NPE at some point
        Optional<String> grantTypeOptional = configService.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.GRANT_TYPE);
        return grantTypeOptional.map(grantType -> payloadBuilders
                        .stream()
                        .filter(impl -> Objects.equals(grantType, impl.getGrantType()))
                        .findFirst()
                        .orElseThrow(() -> new Error("No TokenRequestPayloadBuilder impl supporting oauth grant type: " + grantType)))
                // return no-op payload builder as Provides can't return null
                .orElse(new OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder() {
                    @Override
                    public String getGrantType() {
                        return null;
                    }

                    @Override
                    public HttpContent buildPayload() {
                        return null;
                    }
                });

    }

    @Provides
    static OAuthRefreshTokenSourceAuthStrategy.TokenResponseParser tokenResponseParser(ConfigService configService,
                                                                                       Set<OAuthRefreshTokenSourceAuthStrategy.TokenResponseParser> tokenParser,
                                                                                       OAuthRefreshTokenSourceAuthStrategy.TokenResponseParserImpl defaultParser){
        // Final instantiation of configs are per-function. TOKEN_RESPONSE_TYPE is dependent of type of auth
        // strategy so might not exist for certain functions.
        // If it is mis-configured will throw NPE at some point
        Optional<String> parserNameOptional = configService.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.TOKEN_RESPONSE_TYPE);
        return parserNameOptional.map(parseName -> tokenParser
                        .stream()
                        .filter(impl -> Objects.equals(parseName, impl.getName()))
                        .findFirst()
                        .orElseThrow(() -> new Error("No TokenResponseParser impl with name: " + parserNameOptional)))
                // return no-op payload builder as Provides can't return null
                .orElse(defaultParser);

    }



    @Provides
    static BulkDataSanitizerFactory fileHandler(BulkDataSanitizerFactoryImpl fileHandlerStrategy) {
        return fileHandlerStrategy;
    }

    @Provides
    @Singleton
    DeterministicTokenizationStrategy deterministicPseudonymStrategy(SecretStore secretStore) {
        String salt = secretStore.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT);
        return new Sha256DeterministicTokenizationStrategy(salt);
    }

    @Provides
    @Singleton
    ReversibleTokenizationStrategy pseudonymizationStrategy(SecretStore secretStore,
                                                            DeterministicTokenizationStrategy deterministicTokenizationStrategy) {

        String salt = secretStore.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT);
        Optional<SecretKeySpec> keyFromConfig = secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_ENCRYPTION_KEY)
                .map(passkey -> AESReversibleTokenizationStrategy.aesKeyFromPassword(passkey, salt));
        //q: do we need to support actual fully AES keys?

        if (keyFromConfig.isEmpty()) {
            log.warning("No value for PSOXY_ENCRYPTION_KEY; any transforms depending on it will fail!");
        }

        return AESReversibleTokenizationStrategy.builder()
                .cipherSuite(AESReversibleTokenizationStrategy.CBC)
                .key(keyFromConfig.orElse(null)) //null disables it, which is OK if transforms depending on this aren't used
                .deterministicTokenizationStrategy(deterministicTokenizationStrategy)
                .build();
    }

    @Provides
    @Named("ipEncryptionStrategy")
    @Singleton
    ReversibleTokenizationStrategy ipEncryptionStrategy(SecretStore secretStore,
                                                        @Named("ipHashStrategy") DeterministicTokenizationStrategy deterministicTokenizationStrategy) {
        String salt = secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.SALT_IP)
            .orElse(secretStore.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT));

        Optional<SecretKeySpec> keyFromConfig =
            firstPresent(
                secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.ENCRYPTION_KEY_IP),
                secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_ENCRYPTION_KEY)
            )
            .map(passkey -> AESReversibleTokenizationStrategy.aesKeyFromPassword(passkey, salt));
        //q: do we need to support actual fully AES keys?

        if (keyFromConfig.isEmpty()) {
            log.warning("No value for PSOXY_ENCRYPTION_KEY; any transforms depending on it will fail!");
        }

        return AESReversibleTokenizationStrategy.builder()
            .cipherSuite(AESReversibleTokenizationStrategy.CBC)
            .key(keyFromConfig.orElse(null)) //null disables it, which is OK if transforms depending on this aren't used
            .deterministicTokenizationStrategy(deterministicTokenizationStrategy)
            .build();
    }

    @Provides
    @Named("emailDomains")
    @Singleton
    ReversibleTokenizationStrategy emailDomainsEncryptionStrategy(SecretStore secretStore,
                                                        @Named("emailDomains") DeterministicTokenizationStrategy deterministicTokenizationStrategy) {
        String salt = secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.SALT_EMAIL_DOMAINS)
            .orElse(secretStore.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT));

        Optional<SecretKeySpec> keyFromConfig =
            firstPresent(
                secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.ENCRYPTION_KEY_EMAIL_DOMAINS),
                secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_ENCRYPTION_KEY)
            )
                .map(passkey -> AESReversibleTokenizationStrategy.aesKeyFromPassword(passkey, salt));
        //q: do we need to support actual fully AES keys?

        if (keyFromConfig.isEmpty()) {
            log.warning("No value for PSOXY_ENCRYPTION_KEY; any transforms depending on it will fail!");
        }

        return AESReversibleTokenizationStrategy.builder()
            .cipherSuite(AESReversibleTokenizationStrategy.CBC)
            .key(keyFromConfig.orElse(null)) //null disables it, which is OK if transforms depending on this aren't used
            .deterministicTokenizationStrategy(deterministicTokenizationStrategy)
            .build();
    }

    @Provides
    @Named("ipHashStrategy")
    @Singleton
    DeterministicTokenizationStrategy deterministicTokenizationStrategy(SecretStore secretStore) {
        String salt = secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.SALT_IP)
            .orElse(secretStore.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT));

        return new Sha256DeterministicTokenizationStrategy(salt);
    }



    @Provides
    @Named("emailDomains")
    @Singleton
    DeterministicTokenizationStrategy deterministicTokenizationStrategyEmailDomains(SecretStore secretStore) {
        String salt = secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.SALT_EMAIL_DOMAINS)
            .orElse(secretStore.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT));

        return new Sha256DeterministicTokenizationStrategy(salt);
    }

    @Provides
    @Singleton
    UrlSafeTokenPseudonymEncoder urlSafeTokenPseudonymEncoder() {
        return new UrlSafeTokenPseudonymEncoder();
    }

    @Provides
    @Singleton
    Base64UrlSha256HashPseudonymEncoder base64UrlSha256HashPseudonymEncoder() {
        return new Base64UrlSha256HashPseudonymEncoder();
    }


    @Provides
    @Singleton
    JsonSchemaFilterUtils schemaRuleUtils(EnvVarsConfigService envVarsConfigService) {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        //TODO: probably more proper to override with a 'development' module of some kind
        JsonSchemaFilterUtils.Options.OptionsBuilder options = JsonSchemaFilterUtils.Options.builder();
        options.logRedactions(envVarsConfigService.isDevelopment());

        return new JsonSchemaFilterUtils(objectMapper, options.build());
    }

    @Provides
    @Singleton
    ParameterSchemaUtils parameterSchemaUtils() {
        return new ParameterSchemaUtils();
    }

    @Provides
    Pseudonymizer pseudonymizer(PseudonymizerImplFactory factory,
                                ConfigService config) {
        return factory.create(factory.buildOptions(config));
    }

    @Provides
    @Singleton
    PathTemplateUtils pathTemplateUtils() {
        return new PathTemplateUtils();
    }


    //TODO: utils method for this somewhere??
    @SafeVarargs
    final <T> Optional<T> firstPresent(Optional<T>... optionals) {
        return Stream.of(optionals).filter(Optional::isPresent).findFirst().orElse(Optional.empty());
    }

}
