package co.worklytics.psoxy.gateway.di;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.OAuthAccessTokenSourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.OAuthRefreshTokenSourceAuthStrategy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

import javax.inject.Named;
import javax.inject.Singleton;

@Module
public class GatewayModule {

    @Provides
    @Singleton
    ConfigService providesConfigService(EnvVarsConfigService envVarsConfigService) {
        return envVarsConfigService;
    }

    @Provides @IntoSet
    SourceAuthStrategy providesSourceAuthStrategy(OAuthAccessTokenSourceAuthStrategy oAuthAccessTokenSourceAuthStrategy) {
        return oAuthAccessTokenSourceAuthStrategy;
    }

    @Provides @IntoSet
    SourceAuthStrategy providesSourceAuthStrategy(OAuthRefreshTokenSourceAuthStrategy oAuthRefreshTokenSourceAuthStrategy) {
        return oAuthRefreshTokenSourceAuthStrategy;
    }

}
