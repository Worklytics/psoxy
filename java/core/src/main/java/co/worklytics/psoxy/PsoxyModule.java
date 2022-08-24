package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.storage.FileHandlerFactory;
import co.worklytics.psoxy.storage.impl.FileHandlerFactoryImpl;

import com.avaulta.gateway.pseudonyms.DeterministicPseudonymStrategy;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.pseudonyms.ReversiblePseudonymStrategy;
import com.avaulta.gateway.pseudonyms.impl.AESReversiblePseudonymStrategy;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.pseudonyms.impl.Sha256DeterministicPseudonymStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.api.client.http.HttpContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import dagger.Module;
import dagger.Provides;


import javax.crypto.spec.SecretKeySpec;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * provides implementations for platform-independent dependencies of 'core' module
 *
 */
@Module
public class PsoxyModule {


    @Provides @Singleton //should be thread-safe
    ObjectMapper providesObjectMapper() {
        return new ObjectMapper();
    }

    @Provides
    @Named("ForYAML")
    ObjectMapper providesYAMLObjectMapper() {
        return new ObjectMapper(new YAMLFactory());
    }

    @Provides
    Configuration providesJSONConfiguration(JacksonJsonProvider jacksonJsonProvider,
                                            JacksonMappingProvider jacksonMappingProvider) {
        //jackson here because it's our common JSON stack, but adds dependency beyond the one pkg'd
        // with JsonPath.
        return Configuration.defaultConfiguration()
            .jsonProvider(jacksonJsonProvider)
            .mappingProvider(jacksonMappingProvider);
    }

    @Provides
    JacksonJsonProvider jacksonJsonProvider(ObjectMapper objectMapper) {
        return new JacksonJsonProvider(objectMapper);
    }

    @Provides
    JacksonMappingProvider jacksonMappingProvider(ObjectMapper objectMapper) {
        return new JacksonMappingProvider(objectMapper);
    }

    @Provides
    JsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    @Provides
    static Logger logger() {
        return Logger.getLogger(PsoxyModule.class.getCanonicalName());
    }

    @Provides
    static SourceAuthStrategy sourceAuthStrategy(ConfigService configService, Set<SourceAuthStrategy> sourceAuthStrategies) {
        String identifier = configService.getConfigPropertyOrError(ProxyConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER);
        return sourceAuthStrategies
            .stream()
            .filter(impl -> Objects.equals(identifier, impl.getConfigIdentifier()))
            .findFirst()
            .orElseThrow(() -> new Error("No SourceAuthStrategy impl matching configured identifier: " + identifier));
    }

    @Provides
    static OAuthRefreshTokenSourceAuthStrategy.TokenRequestPayloadBuilder tokenRequestPayloadBuilder(ConfigService configService,
                                                                                                     Set<OAuthRefreshTokenSourceAuthStrategy.TokenRequestPayloadBuilder> payloadBuilders) {
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
            .orElse(new OAuthRefreshTokenSourceAuthStrategy.TokenRequestPayloadBuilder() {
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
    static FileHandlerFactory fileHandler(FileHandlerFactoryImpl fileHandlerStrategy) {
        return fileHandlerStrategy;
    }
    @Provides @Singleton
    DeterministicPseudonymStrategy deterministicPseudonymStrategy(ConfigService config)  {
        String salt = config.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT);
        return new Sha256DeterministicPseudonymStrategy(salt);
    }

    @Provides @Singleton
    ReversiblePseudonymStrategy pseudonymizationStrategy(ConfigService config,
                                                         DeterministicPseudonymStrategy deterministicPseudonymStrategy) {

        String keyFromConfig = config.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_ENCRYPTION_KEY);
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(keyFromConfig), "AES");

        return AESReversiblePseudonymStrategy.builder()
            .cipherSuite(AESReversiblePseudonymStrategy.CBC)
            .key(key)
            .deterministicPseudonymStrategy(deterministicPseudonymStrategy)
            .build();
    }

    @Provides @Singleton
    UrlSafeTokenPseudonymEncoder urlSafeTokenPseudonymEncoder() {
        return new UrlSafeTokenPseudonymEncoder();
    }

}
