package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.UrlEncodedContent;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * implementation of an Access Token Request (per RFC6749 4.4.2) authenticated by an assertion (RFC 7521) based
 * on client id + client secret
 *
 * see
 *   - https://datatracker.ietf.org/doc/html/rfc7521
 *   - https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class ClientCredentialsTokenRequestBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    @Inject
    ConfigService config;

    @Getter(onMethod_ = @Override)
    private final String grantType = "client_credentials";

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        CLIENT_SECRET, //NOTE: you should configure this as a secret in Secret Manager
    }


    public HttpContent buildPayload() {

        Map<String, String> data = new HashMap<>();

        data.put("grant_type", getGrantType());
        data.put("client_id", config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID));
        data.put("client_secret", config.getConfigPropertyOrError(ConfigProperty.CLIENT_SECRET));

        return new UrlEncodedContent(data);

    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
            OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID,
            ConfigProperty.CLIENT_SECRET
            );
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ClientCredentialsWithJWTAssertionGrantTokenRequestBuilder.ConfigProperty.values());
    }
}