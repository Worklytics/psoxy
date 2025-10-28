package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import com.google.auth.Credentials;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.OAuth2CredentialsWithRefresh;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
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

    @Inject
    Clock clock;

    @Getter
    private final String configIdentifier = "oauth2_access_token";

    enum ConfigProperty implements ConfigService.ConfigProperty {
        ACCESS_TOKEN,
    }
    @Inject
    SecretStore secretStore;

    @Override
    public Credentials getCredentials(Optional<String> identityToAssumeForRequestIgnored) {
        identityToAssumeForRequestIgnored.ifPresent(identity -> log.warning("Identity to assume for request ignored for OAuthAccessTokenSourceAuthStrategy"));

        String token = secretStore.getConfigPropertyOrError(ConfigProperty.ACCESS_TOKEN);
        // Some date into far future. Expiration is required
        Instant expire = clock.instant().plus(365L, ChronoUnit.DAYS);
        AccessToken accessToken = new AccessToken(token, Date.from(expire));
        // OAuth2Credentials tried to refresh and fail
        OAuth2CredentialsWithRefresh.Builder builder = OAuth2CredentialsWithRefresh.newBuilder();
        builder.setAccessToken(accessToken);
        // refresh does nothing, just return the same token
        builder.setRefreshHandler(() -> accessToken);
        return builder.build();
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(ConfigProperty.values());
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }
}
