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
public class AccountCredentialsGrantTokenRequestBuilder implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    public static final String GRANT_TYPE = "account_credentials";

    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;

    private static final String PARAM_ACCOUNT_ID = "account_id";

    private static final String PARAM_GRANT_TYPE = "grant_type";

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(ConfigProperty.ACCOUNT_ID, ConfigProperty.CLIENT_ID, ConfigProperty.CLIENT_SECRET);
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        // not strictly secret; but leave it to customer discretion; (will check config, fail to SecretStore if absent)
        ACCOUNT_ID,
        CLIENT_ID,
        // secrets
        CLIENT_SECRET,
        ;
    }

    @Getter(onMethod_ = @Override)
    private final String grantType = GRANT_TYPE;

    @Override
    public HttpContent buildPayload() {

        //trim to avoid copy-paste errors
        //q : check for non-printable chars or anything like that
        String accountId = StringUtils.trim(config.getConfigPropertyAsOptional(ConfigProperty.ACCOUNT_ID)
            .orElseGet(() -> secretStore.getConfigPropertyOrError(ConfigProperty.ACCOUNT_ID)));

        // The documentation doesn't say anything to use POST data, but passes everything in the URL
        // Tested manually and, for the moment, it is accepted as POST data
        Map<String, String> data = new TreeMap<>();
        data.put(PARAM_GRANT_TYPE, getGrantType());
        data.put(PARAM_ACCOUNT_ID, accountId);
        return new UrlEncodedContent(data);
    }

    @Override
    public void addHeaders(HttpHeaders httpHeaders) {
        String clientId = StringUtils.trim(config.getConfigPropertyAsOptional(ConfigProperty.CLIENT_ID)
            .orElseGet(() -> secretStore.getConfigPropertyOrError(ConfigProperty.CLIENT_ID)));

        String clientSecret = StringUtils.trim(secretStore.getConfigPropertyOrError(ConfigProperty.CLIENT_SECRET));
        AuthUtils.setBasicAuthHeader(httpHeaders, clientId, clientSecret);
    }

}
