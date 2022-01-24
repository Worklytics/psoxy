package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2Credentials;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.util.Optional;
import java.util.Set;

/**
 * source auth strategy to authenticate using a long-lived OAuth 2.0 access token to authenticate
 * against API
 *
 * (NOTE: many APIs offer only short-lived access tokens, which must be periodically refreshed. in
 * such scenarios OAuthRefreshTokenSourceAuthStrategy is more appropriate
 *
 * @see OAuthRefreshTokenSourceAuthStrategy
 *
 */
@Log
@NoArgsConstructor(onConstructor_ = @Inject)
public class OAuthAccessTokenSourceAuthStrategy implements SourceAuthStrategy {

    @Getter
    private final String configIdentifier = "oauth2_access_token";

    enum ConfigProperty implements ConfigService.ConfigProperty {
        ACCESS_TOKEN,
    }

    @Inject ConfigService config;

    @Override
    public Credentials getCredentials(Optional<String> userToImpersonate) {
        String token = config.getConfigPropertyOrError(ConfigProperty.ACCESS_TOKEN);
        return OAuth2Credentials.create(new AccessToken(token, null));
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(ConfigProperty.values());
    }
}
