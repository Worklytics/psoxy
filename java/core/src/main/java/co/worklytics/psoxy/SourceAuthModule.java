package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.GoogleCloudPlatformServiceAccountKeyAuthStrategy;
import co.worklytics.psoxy.gateway.impl.OAuthAccessTokenSourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.OAuthRefreshTokenSourceAuthStrategy;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public class SourceAuthModule {

    @Provides
    @IntoSet
    SourceAuthStrategy providesOAuthAccessTokenSourceAuthStrategy(OAuthAccessTokenSourceAuthStrategy oAuthAccessTokenSourceAuthStrategy) {
        return oAuthAccessTokenSourceAuthStrategy;
    }

    @Provides @IntoSet
    SourceAuthStrategy providesOAuthRefreshTokenSourceAuthStrategy(OAuthRefreshTokenSourceAuthStrategy oAuthRefreshTokenSourceAuthStrategy) {
        return oAuthRefreshTokenSourceAuthStrategy;
    }

    @Provides
    @IntoSet
    SourceAuthStrategy providesSourceAuthStrategy(GoogleCloudPlatformServiceAccountKeyAuthStrategy googleCloudPlatformServiceAccountKeyAuthStrategy) {
        return googleCloudPlatformServiceAccountKeyAuthStrategy;
    }

    @Provides
    OAuth2CredentialsWithRefresh.OAuth2RefreshHandler providesOAuth2RefreshHandler(OAuthRefreshTokenSourceAuthStrategy.RefreshHandlerImpl refreshHandler) {
        return refreshHandler;
    }
}
