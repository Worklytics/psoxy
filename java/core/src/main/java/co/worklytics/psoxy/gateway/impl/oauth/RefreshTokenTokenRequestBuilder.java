package co.worklytics.psoxy.gateway.impl.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.UrlEncodedContent;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import co.worklytics.psoxy.gateway.SecretStore;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * build payload for canonical OAuth access token request authenticated by a long-lived refresh
 * token + client secret
 * <p>
 * (apologies for awkward name, but to be clear: this is using one kind of token to request another,
 * so it's a token request, using a token)
 *
 * @see OAuthAccessTokenSourceAuthStrategy
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class RefreshTokenTokenRequestBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;

    @Getter(onMethod_ = @Override)
    private final String grantType = "refresh_token";

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        REFRESH_TOKEN, //NOTE: you should configure this as a secret in Secret Manager
        CLIENT_SECRET, //NOTE: you should configure this as a secret in Secret Manager

    }


    public HttpContent buildPayload() {

        Map<String, String> data = new HashMap<>();

        data.put("grant_type", getGrantType());
        data.put("refresh_token", secretStore.getConfigPropertyOrError(ConfigProperty.REFRESH_TOKEN));
        data.put("client_id", getClientId());
        data.put("client_secret", secretStore.getConfigPropertyOrError(ConfigProperty.CLIENT_SECRET));

        return new UrlEncodedContent(data);

    }

    private String getClientId() {
        return config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID)
                .orElseGet(() -> secretStore.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID));

    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
                OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID,
                ConfigProperty.CLIENT_SECRET,
                ConfigProperty.REFRESH_TOKEN
        );
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.values());
    }

    @Override
    public void addHeaders(HttpHeaders httpHeaders) {
        // For refresh token, format will always be in JSON
        // In theory it should not be required to specify it
        // as it is part of the standard (see https://datatracker.ietf.org/doc/html/rfc6749#section-5.1)
        // But for some APIs (Github) is required to ensure that the response will be in JSON format
        httpHeaders.setAccept("application/json");
    }
}
