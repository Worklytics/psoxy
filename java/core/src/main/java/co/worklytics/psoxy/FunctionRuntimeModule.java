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
import java.time.Clock;
import java.time.Duration;
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
    HttpTransportFactory providesHttpTransportFactory() {
        //atm, all function runtimes expected to use generic java NetHttpTransport
        return () -> new NetHttpTransport();
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
