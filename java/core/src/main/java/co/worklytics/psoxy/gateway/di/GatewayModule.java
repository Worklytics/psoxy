package co.worklytics.psoxy.gateway.di;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.OAuthAccessTokenSourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.OAuthRefreshTokenSourceAuthStrategy;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
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
    SourceAuthStrategy providesOAuthAccessTokenSourceAuthStrategy(OAuthAccessTokenSourceAuthStrategy oAuthAccessTokenSourceAuthStrategy) {
        return oAuthAccessTokenSourceAuthStrategy;
    }

    @Provides @IntoSet
    SourceAuthStrategy providesOAuthRefreshTokenSourceAuthStrategy(OAuthRefreshTokenSourceAuthStrategy oAuthRefreshTokenSourceAuthStrategy) {
        return oAuthRefreshTokenSourceAuthStrategy;
    }

    @Provides
    OAuth2CredentialsWithRefresh.OAuth2RefreshHandler providesOAuth2RefreshHandler(OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl refreshHandler) {
        return refreshHandler;
    }

    @Provides
    HttpRequestFactory providesHttpRequestFactory() {
        return (new NetHttpTransport()).createRequestFactory();
    }

}
