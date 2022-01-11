package co.worklytics.psoxy.gateway.di;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public class GatewayModule {

    @Provides
    @Singleton
    ConfigService providesConfigService(EnvVarsConfigService envVarsConfigService) {
        return envVarsConfigService;
    }

}
