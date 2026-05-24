package co.worklytics.psoxy;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.WebhookCollectionRules;
import com.avaulta.gateway.rules.augments.SentenceMetadataProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpTransportFactory;
import co.worklytics.psoxy.gateway.ApiModeConfig;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LoggingConfiguration;
import co.worklytics.psoxy.gateway.ProcessedDataStage;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.ProxyConstants;
import co.worklytics.psoxy.gateway.WebhookCollectorModeConfig;
import co.worklytics.psoxy.gateway.auth.Base64KeyClient;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import co.worklytics.psoxy.gateway.impl.CompositeResourceService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.LocalFileResourceService;
import co.worklytics.psoxy.gateway.impl.WebhookSanitizer;
import co.worklytics.psoxy.gateway.impl.output.NoOutput;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.ApiDataSideOutput;
import co.worklytics.psoxy.gateway.output.ApiSanitizedDataOutput;
import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.impl.WebhookSanitizerImplFactory;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import co.worklytics.psoxy.utils.RandomNumberGeneratorImpl;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import lombok.SneakyThrows;

/**
 * generic dep bindings for an actual Function runtime environment (as opposed to a testing runtime)
 *
 *
 */
@Module(
    includes = {
        FunctionRuntimeModule.Bindings.class,
    }
)
public class FunctionRuntimeModule {

    @Provides
    @Singleton
    static Clock clock() {
        return Clock.systemUTC();
    }

    @Provides
    static UUID randomUUID() {
        return UUID.randomUUID();
    }

    // TODO: move to Bindings
    @Provides @Singleton
    static RandomNumberGenerator randomNumberGenerator() {
        //to be clear, NOT for cryptography
        return new RandomNumberGeneratorImpl();
    }

    @Provides
    @Singleton
    static ProxyConstants providesProxyConstants(ConfigService configService) {
        String userAgent = configService.getConfigPropertyAsOptional(ProxyConfigProperty.USER_AGENT)
                .orElse(ProxyConstants.buildDefaultUserAgent());

        return ProxyConstants.builder()
            .userAgent(userAgent)
            .build();
    }

    @Provides
    static HttpRequestFactory providesHttpRequestFactory(HttpTransportFactory httpTransportFactory, ProxyConstants proxyConstants) {
        return httpTransportFactory.create().createRequestFactory(request -> {
            request.getHeaders().setUserAgent(proxyConstants.getUserAgent());
        });
    }

    // q: should we just replace this with a Provider<HttpTransport>, rather than having more coupling to google-http-client classes?
    @Provides @Singleton
    HttpTransportFactory providesHttpTransportFactory(ApiModeConfig apiModeConfig) {
        final String sslContextProtocol = apiModeConfig.getTlsVersion();
        if (Arrays.stream(ApiModeConfig.TlsVersions.ALL).noneMatch(s -> sslContextProtocol.equals(s))) {
            throw new IllegalArgumentException("Invalid TLS version: " + sslContextProtocol);
        }

        return () -> {
            try {
                SSLContext sslContext = SSLContext.getInstance(sslContextProtocol);
                sslContext.init(null, null, new java.security.SecureRandom());
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                // Configure the NetHttpTransport to use the custom SSL context
                return new NetHttpTransport.Builder()
                    .setSslSocketFactory(sslSocketFactory)
                    .build();
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                throw new RuntimeException("Failed to create custom SSL context with " + sslContextProtocol, e);
            }
        };
    }

    @Provides @Singleton
    static ConfigService configService(EnvVarsConfigService envVarsConfigService,
                                       @Named("Native") ConfigService nativeConfigService) {
        return CompositeConfigService.builder()
            .preferred(envVarsConfigService)
            .fallback(nativeConfigService)
            .build();
    }

    /**
     * Provides the instance-scoped ResourceService, composing local FS with the
     * platform-specific remote ResourceService (S3/GCS), using failover semantics.
     *
     * <p>Failover order: local filesystem ({@link ResourceService#DEFAULT_LOCAL_RESOURCE_PATH})
     * → remote cloud storage → no-op</p>
     *
     * <p>The local FS path is always checked first (no env var needed). It can be populated
     * via deployment layers, Lambda layers, init scripts, etc.</p>
     */
    @Provides @Singleton
    static ResourceService instanceResourceService(@Named("Remote") ResourceService remoteResourceService,
                                                   @Named("SharedRemote") ResourceService sharedRemoteResourceService) {
        // always layer local FS on top of remote — local is a fast path / override
        ResourceService instanceResourceService = CompositeResourceService.builder()
            .preferred(new LocalFileResourceService(ResourceService.DEFAULT_LOCAL_RESOURCE_PATH))
            .fallback(remoteResourceService)
            .build();
        ResourceService openNlpResourceService = CompositeResourceService.builder()
            .preferred(instanceResourceService)
            .fallback(sharedRemoteResourceService)
            .build();
        SentenceMetadataProcessor.configureResourceService(openNlpResourceService);
        return instanceResourceService;
    }

    @Provides @Singleton @Named("async")
    static ApiSanitizedDataOutput apiSanitizedDataOutput(OutputUtils outputUtils) {
        return outputUtils.asyncOutput();
    }


    @Provides @Singleton @Named("forOriginal")
    static ApiDataSideOutput sideOutputForOriginal(OutputUtils sideOutputUtil) {
        return sideOutputUtil.forStage(ProcessedDataStage.ORIGINAL);
    }

    @Provides @Singleton @Named("forSanitized")
    static ApiDataSideOutput sideOutputForSanitized(OutputUtils outputUtils) {
        return outputUtils.forStage(ProcessedDataStage.SANITIZED);
    }

    @Provides @Singleton  @Named("forWebhooks")
    static Output output(OutputUtils outputUtils) {
        return outputUtils.forIncomingWebhooks();
    }

    @Provides @Singleton @Named("forWebhookQueue")
    static Output webhookQueueOutput(OutputUtils outputUtils) {
        return outputUtils.forBatchedWebhookContent();
    }


    @Provides @Singleton
    static NoOutput noOutput() {
        return new NoOutput();
    }

    @SneakyThrows
    @Provides @Singleton WebhookSanitizer webhookSanitizer(WebhookSanitizerImplFactory webhookSanitizerFactory,
                                                           ConfigService configService,
                                                           @Named("ForYAML") ObjectMapper objectMapper,
                                                           co.worklytics.psoxy.rules.RulesUtils rulesUtils) {
        String rulesStr = configService.getConfigPropertyOrError(ProxyConfigProperty.RULES);
        String yamlEncodedRules = rulesUtils.decodeToYaml(rulesStr);
        return webhookSanitizerFactory.create(objectMapper.readerFor(WebhookCollectionRules.class).readValue(yamlEncodedRules));
    }

    @Provides @Singleton
    static WebhookCollectorModeConfig webhookCollectorModeConfig(ConfigService configService) {
        return WebhookCollectorModeConfig.fromConfigService(configService);
    }

    @Provides @Singleton
    static ApiModeConfig apiModeConfig(ConfigService configService) {
        return ApiModeConfig.fromConfigService(configService);
    }

    @Provides @Singleton
    static LoggingConfiguration loggingConfiguration(ConfigService configService) {
        return LoggingConfiguration.fromConfigService(configService);
    }

    //q: right place for this?
    @Module
    public abstract class Bindings {

        @Binds
        @IntoSet
        abstract PublicKeyStoreClient base64KeyClient(Base64KeyClient base64KeyClient);
    }
}
