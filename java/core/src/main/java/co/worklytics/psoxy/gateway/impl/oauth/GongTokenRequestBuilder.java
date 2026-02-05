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
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Supports <a href="https://help.gong.io/docs/create-an-app-for-gong">refresh token renewal</a> for Gong
 *
 * @see OAuthAccessTokenSourceAuthStrategy
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class GongTokenRequestBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    private static final Map<String, String> EMPTY_MAP = Collections.emptyMap();

    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;

    @Getter(onMethod_ = @Override)
    private final String grantType = "gong_refresh_token";

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
        return Set.of(ConfigProperty.values());
    }

    @Override
    public void addHeaders(HttpHeaders httpHeaders) {
        String clientId = StringUtils.trim(config.getConfigPropertyAsOptional(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID)
            .orElseGet(() -> secretStore.getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_ID)));

        String clientSecret = StringUtils.trim(secretStore.getConfigPropertyOrError(AccountCredentialsGrantTokenRequestBuilder.ConfigProperty.CLIENT_SECRET));

        AuthUtils.setBasicAuthHeader(httpHeaders, clientId, clientSecret);
    }
}
