package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.GoogleCloudPlatformServiceAccountKeyAuthStrategy;
import co.worklytics.psoxy.gateway.impl.oauth.ClientCredentialsGrantTokenRequestPayloadBuilder;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthAccessTokenSourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.oauth.RefreshTokenPayloadBuilder;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

/**
 *
 * q: can this be made abstract class, and all provider methods converted to `abstract + @Binds`??
 */
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
    OAuth2CredentialsWithRefresh.OAuth2RefreshHandler providesOAuth2RefreshHandler(OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl refreshHandler) {
        return refreshHandler;
    }

    @Provides @IntoSet
    OAuthRefreshTokenSourceAuthStrategy.TokenRequestPayloadBuilder refreshTokenPayloadBuilder(RefreshTokenPayloadBuilder refreshTokenPayloadBuilder) {
        return refreshTokenPayloadBuilder;
    }

    @Provides @IntoSet
    OAuthRefreshTokenSourceAuthStrategy.TokenRequestPayloadBuilder clientCredentialsGrantTokenRequestPayloadBuilder(ClientCredentialsGrantTokenRequestPayloadBuilder refreshTokenPayloadBuilder) {
        return refreshTokenPayloadBuilder;
    }
}
