package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.UrlEncodedContent;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Implemented for Zoom use case
 * {@link "https://marketplace.zoom.us/docs/guides/build/server-to-server-oauth-app/"}
 * Server-to-server OAuth app
 * {@link "https://marketplace.zoom.us/docs/guides/build/server-to-server-oauth-app/#use-account-credentials-to-get-an-access-token"}
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class AccountCredentialsGrantTokenRequestPayloadBuilder implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestPayloadBuilder, RequiresConfiguration {

    @Inject
    ConfigService config;

    private static final String PARAM_ACCOUNT_ID = "account_id";

    private static final String PARAM_GRANT_TYPE = "grant_type";

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(ConfigProperty.ACCOUNT_ID);
    }

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        ACCOUNT_ID, //NOTE: you should configure this as a secret in Secret Manager
        CLIENT_ID,  //NOTE: you should configure this as a secret in Secret Manager
        CLIENT_SECRET //NOTE: you should configure this as a secret in Secret Manager
    }

    @Override
    public String getGrantType() {
        return "account_credentials";
    }

    @Override
    public HttpContent buildPayload() {
        // The documentation doesn't say anything to use POST data, but passes everything in the URL
        // Tested manually and, for the moment, it is accepted as POST data
        Map<String, String> data = new TreeMap<>();
        data.put(PARAM_GRANT_TYPE, getGrantType());
        data.put(PARAM_ACCOUNT_ID, config.getConfigPropertyOrError(ConfigProperty.ACCOUNT_ID));
        return new UrlEncodedContent(data);
    }

    @Override
    public void addHeaders(HttpHeaders httpHeaders) {
        String clientId = config.getConfigPropertyOrError(ConfigProperty.CLIENT_ID);
        String clientSecret = config.getConfigPropertyOrError(ConfigProperty.CLIENT_SECRET);
        String token = Base64.getEncoder()
            .encodeToString(String.join(":", clientId, clientSecret).getBytes(StandardCharsets.UTF_8));
        httpHeaders.setAuthorization("Basic " + token);
    }

}
