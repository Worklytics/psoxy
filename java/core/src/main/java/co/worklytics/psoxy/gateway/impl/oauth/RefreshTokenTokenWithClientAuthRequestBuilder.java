package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import co.worklytics.psoxy.gateway.SecretStore;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.UrlEncodedContent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Supports <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-2.3.1">Client Authentication</a>
 * with Refresh Token Grant Type and client id + client secret as Basic authentication
 * for  <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-6">refreshing the token</a>
 *
 * @see OAuthAccessTokenSourceAuthStrategy
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class RefreshTokenTokenWithClientAuthRequestBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();

    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;

    @Getter(onMethod_ = @Override)
    private final String grantType = "refresh_token_client_auth";

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        REFRESH_TOKEN, //NOTE: you should configure this as a secret in Secret Manager
        CLIENT_SECRET, //NOTE: you should configure this as a secret in Secret Manager
    }

    @Override
    public String addQueryParameters(String url) {
       return url + "?grant_type=refresh_token&refresh_token=" + secretStore.getConfigPropertyOrError(RefreshTokenTokenRequestBuilder.ConfigProperty.REFRESH_TOKEN);
    }

    public HttpContent buildPayload() {
        return new UrlEncodedContent(EMPTY_MAP);
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
        String clientId = StringUtils.trim(config.getConfigPropertyAsOptional(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID)
            .orElseGet(() -> secretStore.getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID)));

        String clientSecret = StringUtils.trim(secretStore.getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_SECRET));

        AuthUtils.setBasicAuthHeader(httpHeaders, clientId, clientSecret);
    }
}
