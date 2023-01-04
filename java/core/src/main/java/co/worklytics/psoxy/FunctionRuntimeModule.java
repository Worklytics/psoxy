package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.*;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
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

    @Provides
    static HttpRequestFactory providesHttpRequestFactory() {
        //atm, all function runtimes expected to use generic java NetHttpTransport
        return (new NetHttpTransport()).createRequestFactory();
    }
    @Provides @Singleton
    static ConfigService configService(EnvVarsConfigService envVarsConfigService,
                                       @Named("Native") ConfigService nativeConfigService,
                                       VaultConfigServiceFactory vaultConfigServiceFactory) {

        String sharedPath
            = envVarsConfigService
            .getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
            .orElse("");


        String connectorPath
            = envVarsConfigService
            .getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_CONNECTOR_CONFIG)
            .orElse("");

        ConfigService remoteConfigService;
        if (vaultConfigServiceFactory.isVaultConfigured(envVarsConfigService)) {
            Duration sharedTtl = Duration.ofMinutes(20);
            Duration connectorTtl = Duration.ofMinutes(5);

            VaultConfigService sharedVault = vaultConfigServiceFactory.create(sharedPath);
            VaultConfigService connectorVault = vaultConfigServiceFactory.create(connectorPath);

            //initialize vaults (q: right place for this??
            // alternatives:
            //   - in the VaultConfigServiceFactory (but hard to make it generated by Dagger then)
            //   - generically expose init() in ConfigService interface, and call it later before
            //     use (but nothing but vault needs it atm
            sharedVault.init();
            connectorVault.init();

            CompositeConfigService vaultConfigService = CompositeConfigService.builder()
                .preferred(new CachingConfigServiceDecorator(connectorVault, connectorTtl))
                .fallback(new CachingConfigServiceDecorator(sharedVault, sharedTtl))
                .build();

            remoteConfigService = CompositeConfigService.builder()
                .preferred(vaultConfigService)
                //fallback to native, if not defined in Vault
                .fallback(nativeConfigService)
                .build();
        } else {
            remoteConfigService = nativeConfigService;
        }

        return CompositeConfigService.builder()
            .preferred(envVarsConfigService)
            .fallback(remoteConfigService)
            .build();
    }


}
