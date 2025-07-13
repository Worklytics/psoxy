package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.BasicAuthStrategy;
import co.worklytics.psoxy.gateway.impl.GoogleCloudPlatformServiceAccountKeyAuthStrategy;
import co.worklytics.psoxy.gateway.impl.oauth.*;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
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
    SourceAuthStrategy providesOAuthAccessTokenSourceAuthStrategy(
            OAuthAccessTokenSourceAuthStrategy oAuthAccessTokenSourceAuthStrategy) {
        return oAuthAccessTokenSourceAuthStrategy;
    }

    @Provides
    @IntoSet
    SourceAuthStrategy providesOAuthRefreshTokenSourceAuthStrategy(
            OAuthRefreshTokenSourceAuthStrategy oAuthRefreshTokenSourceAuthStrategy) {
        return oAuthRefreshTokenSourceAuthStrategy;
    }

    @Provides
    @IntoSet
    SourceAuthStrategy providesSourceAuthStrategy(
            GoogleCloudPlatformServiceAccountKeyAuthStrategy googleCloudPlatformServiceAccountKeyAuthStrategy) {
        return googleCloudPlatformServiceAccountKeyAuthStrategy;
    }

    @Provides
    @IntoSet
    SourceAuthStrategy providesBasicAuthStrategy(BasicAuthStrategy basicAuthStrategy) {
        return basicAuthStrategy;
    }

    @Provides
    OAuth2CredentialsWithRefresh.OAuth2RefreshHandler providesOAuth2RefreshHandler(
            OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl refreshHandler) {
        return refreshHandler;
    }

    @Provides
    @IntoSet
    OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder refreshTokenPayloadBuilder(
            RefreshTokenTokenRequestBuilder refreshTokenPayloadBuilder) {
        return refreshTokenPayloadBuilder;
    }

    @Provides
    @IntoSet
    OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder clientCredentialsGrantTokenRequestPayloadBuilder(
            ClientCredentialsGrantTokenRequestBuilder clientCredentialsGrantTokenRequestBuilder) {
        return clientCredentialsGrantTokenRequestBuilder;
    }

    @Provides
    @IntoSet
    OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder accountCredentialsGrantTokenRequestPayloadBuilder(
            AccountCredentialsGrantTokenRequestBuilder refreshTokenPayloadBuilder) {
        return refreshTokenPayloadBuilder;
    }

    @Provides
    @IntoSet
    OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder certificateCredentialsGrantTokenRequestPayloadBuilder(
            CertificateGrantTokenRequestBuilder certificateGrantTokenRequestBuilder) {
        return certificateGrantTokenRequestBuilder;
    }

    @Provides
    @IntoSet
    OAuthRefreshTokenSourceAuthStrategy.TokenResponseParser tokenResponseParser(
            OAuthRefreshTokenSourceAuthStrategy.TokenResponseParserImpl instance) {
        return instance;
    }

    @Provides
    @IntoSet
    OAuthRefreshTokenSourceAuthStrategy.TokenResponseParser githubResponseParser(
            GithubAccessTokenResponseParserImpl instance) {
        return instance;
    }
}
