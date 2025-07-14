package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.auth.Base64KeyClient;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import co.worklytics.psoxy.gateway.impl.*;
import co.worklytics.psoxy.gateway.impl.output.NoOutput;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.ApiDataSideOutput;
import co.worklytics.psoxy.gateway.output.ApiSanitizedDataOutput;
import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.impl.WebhookSanitizerImplFactory;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import co.worklytics.psoxy.utils.RandomNumberGeneratorImpl;
import com.avaulta.gateway.rules.WebhookCollectionRules;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpTransportFactory;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import lombok.SneakyThrows;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Arrays;
import java.util.UUID;

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
    static HttpRequestFactory providesHttpRequestFactory(HttpTransportFactory httpTransportFactory) {
        return httpTransportFactory.create().createRequestFactory();
    }

    // q: should we just replace this with a Provider<HttpTransport>, rather than having more coupling to google-http-client classes?
    @Provides @Singleton
    HttpTransportFactory providesHttpTransportFactory(EnvVarsConfigService envVarsConfigService) {
        final String sslContextProtocol =
            envVarsConfigService.getConfigPropertyAsOptional(ApiModeConfigProperty.TLS_VERSION)
                .orElse(ApiModeConfigProperty.TlsVersions.TLSv1_3);
        if (Arrays.stream(ApiModeConfigProperty.TlsVersions.ALL).noneMatch(s -> sslContextProtocol.equals(s))) {
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
                                                           @Named("ForYAML") ObjectMapper objectMapper) {
        return webhookSanitizerFactory.create(objectMapper.readerFor(WebhookCollectionRules.class).readValue(configService.getConfigPropertyOrError(ProxyConfigProperty.RULES)));
    }

    //q: right place for this?
    @Module
    public abstract class Bindings {

        @Binds
        @IntoSet
        abstract PublicKeyStoreClient base64KeyClient(Base64KeyClient base64KeyClient);
    }
}
