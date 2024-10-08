package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.*;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import co.worklytics.psoxy.utils.RandomNumberGeneratorImpl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpTransportFactory;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * generic dep bindings for an actual Function runtime environment (as opposed to a testing runtime)
 *
 *
 */
@Module
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

    @Provides @Singleton
    static RandomNumberGenerator randomNumberGenerator() {
        //to be clear, NOT for cryptography
        return new RandomNumberGeneratorImpl();
    }

    @Provides
    static HttpRequestFactory providesHttpRequestFactory(HttpTransportFactory httpTransportFactory) {
        return httpTransportFactory.create().createRequestFactory();
    }

    @Provides @Singleton
    HttpTransportFactory providesHttpTransportFactory(EnvVarsConfigService envVarsConfigService) {
        final String sslContextProtocol =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.TLS_VERSION)
                .orElse(ProxyConfigProperty.TlsVersions.TLSv1_3);
        if (Arrays.stream(ProxyConfigProperty.TlsVersions.ALL).noneMatch(s -> sslContextProtocol.equals(s))) {
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


}
