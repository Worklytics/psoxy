package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

/**
 * Non-secret OAuth refresh-token source auth settings loaded at startup.
 */
@Value
@Builder
public class OAuthRefreshTokenSourceAuthConfig {

    public static OAuthRefreshTokenSourceAuthConfig fromConfigService(ConfigService configService) {
        OAuthRefreshTokenSourceAuthConfigBuilder builder = OAuthRefreshTokenSourceAuthConfig.builder();
        builder.refreshEndpoint(configService.getConfigPropertyOrError(
            OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT));
        configService.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID)
            .ifPresent(builder::clientId);
        configService.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.GRANT_TYPE)
            .ifPresent(builder::grantType);
        configService.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.USE_SHARED_TOKEN)
            .map(Boolean::parseBoolean)
            .ifPresent(builder::useSharedToken);
        configService.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.ACCESS_TOKEN_CACHEABLE)
            .map(Boolean::parseBoolean)
            .ifPresent(builder::accessTokenCacheable);
        configService.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.TOKEN_RESPONSE_TYPE)
            .ifPresent(builder::tokenResponseType);
        return builder.build();
    }

    String refreshEndpoint;
    String clientId;
    String grantType;
    Boolean useSharedToken;
    Boolean accessTokenCacheable;
    String tokenResponseType;

    public Optional<String> getRefreshEndpoint() {
        return Optional.ofNullable(refreshEndpoint);
    }

    public Optional<String> getClientId() {
        return Optional.ofNullable(clientId);
    }

    public Optional<String> getGrantType() {
        return Optional.ofNullable(grantType);
    }

    public Optional<Boolean> getUseSharedToken() {
        return Optional.ofNullable(useSharedToken);
    }

    public Optional<Boolean> getAccessTokenCacheable() {
        return Optional.ofNullable(accessTokenCacheable);
    }

    public Optional<String> getTokenResponseType() {
        return Optional.ofNullable(tokenResponseType);
    }
}
