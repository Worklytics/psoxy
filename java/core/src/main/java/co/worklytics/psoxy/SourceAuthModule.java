package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.AwsWifGoogleCloudProcessIdentity;
import co.worklytics.psoxy.gateway.impl.BasicAuthStrategy;
import co.worklytics.psoxy.gateway.impl.ClaudeAuthStrategy;
import co.worklytics.psoxy.gateway.impl.GcpHostedProcessIdentity;
import co.worklytics.psoxy.gateway.impl.GcpIamSignJwtAuthStrategy;
import co.worklytics.psoxy.gateway.impl.GoogleCloudPlatformServiceAccountKeyAuthStrategy;
import co.worklytics.psoxy.gateway.impl.GoogleCloudProcessIdentity;
import co.worklytics.psoxy.gateway.impl.WindsurfServiceKeyAuthStrategy;
import co.worklytics.psoxy.gateway.impl.oauth.*;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;

@Module
public abstract class SourceAuthModule {

    @Binds
    @IntoSet
    abstract SourceAuthStrategy oauthAccessTokenSourceAuthStrategy(
        OAuthAccessTokenSourceAuthStrategy impl);

    @Binds
    @IntoSet
    abstract SourceAuthStrategy oauthRefreshTokenSourceAuthStrategy(
        OAuthRefreshTokenSourceAuthStrategy impl);

    @Binds
    @IntoSet
    abstract SourceAuthStrategy googleCloudPlatformServiceAccountKeyAuthStrategy(
        GoogleCloudPlatformServiceAccountKeyAuthStrategy impl);

    @Binds
    @IntoSet
    abstract SourceAuthStrategy gcpIamSignJwtAuthStrategy(GcpIamSignJwtAuthStrategy impl);

    @Binds
    @IntoSet
    abstract GoogleCloudProcessIdentity gcpHostedProcessIdentity(GcpHostedProcessIdentity impl);

    @Binds
    @IntoSet
    abstract GoogleCloudProcessIdentity awsWifGoogleCloudProcessIdentity(
        AwsWifGoogleCloudProcessIdentity impl);

    @Binds
    @IntoSet
    abstract SourceAuthStrategy basicAuthStrategy(BasicAuthStrategy impl);

    @Binds
    @IntoSet
    abstract SourceAuthStrategy claudeAuthStrategy(ClaudeAuthStrategy impl);

    @Binds
    @IntoSet
    abstract SourceAuthStrategy windsurfServiceKeyAuthStrategy(WindsurfServiceKeyAuthStrategy impl);

    @Binds
    abstract OAuth2CredentialsWithRefresh.OAuth2RefreshHandler oauth2RefreshHandler(
        OAuthRefreshTokenSourceAuthStrategy.TokenRefreshHandlerImpl impl);

    @Binds
    @IntoSet
    abstract OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder refreshTokenTokenRequestBuilder(
        RefreshTokenTokenRequestBuilder impl);

    @Binds
    @IntoSet
    abstract OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder refreshTokenRequestViaQueryParameterBuilder(
        RefreshTokenRequestViaQueryParameterBuilder impl);

    @Binds
    @IntoSet
    abstract OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder clientCredentialsGrantTokenRequestBuilder(
        ClientCredentialsGrantTokenRequestBuilder impl);

    @Binds
    @IntoSet
    abstract OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder accountCredentialsGrantTokenRequestBuilder(
        AccountCredentialsGrantTokenRequestBuilder impl);

    @Binds
    @IntoSet
    abstract OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder certificateGrantTokenRequestBuilder(
        CertificateGrantTokenRequestBuilder impl);

    @Binds
    @IntoSet
    abstract OAuthRefreshTokenSourceAuthStrategy.TokenResponseParser tokenResponseParser(
        OAuthRefreshTokenSourceAuthStrategy.TokenResponseParserImpl impl);

    @Binds
    @IntoSet
    abstract OAuthRefreshTokenSourceAuthStrategy.TokenResponseParser githubAccessTokenResponseParser(
        GithubAccessTokenResponseParserImpl impl);
}
