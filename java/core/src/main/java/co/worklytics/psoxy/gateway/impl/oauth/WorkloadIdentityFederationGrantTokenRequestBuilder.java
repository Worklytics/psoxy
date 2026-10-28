package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.UrlEncodedContent;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.inject.Inject;
import lombok.Getter;
import lombok.SneakyThrows;

/**
 * implementation of a "workload identity" credentials.
 * This is same as client credentials with the difference that the JWT assertion needs to be externally provided and
 * claims "aud" and "iss" should be the same between two parts.
 *
 * @see <a href="https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-client-creds-grant-flow#third-case-access-token-request-with-a-federated-credential">...</a>
 * as an example of MSFT implementation
 *
 * - <a href="https://datatracker.ietf.org/doc/html/rfc7521">...</a>
 * - <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2">...</a>
 */
public abstract class WorkloadIdentityFederationGrantTokenRequestBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    protected WorkloadIdentityFederationGrantTokenRequestBuilder(
            OAuthRefreshTokenSourceAuthConfig oauthConfig,
            WorkloadIdentityFederationGrantConfig wifConfig) {
        this.oauthConfig = oauthConfig;
        this.wifConfig = wifConfig;
    }

    @Getter
    public enum ConfigProperty implements ConfigService.ConfigProperty {
        CLIENT_ID,
        TOKEN_SCOPE,
        ;

        @Getter(onMethod_ = @Override)
        private final SupportedSource supportedSource = SupportedSource.ENV_VAR;
    }

    @Getter(onMethod_ = @Override)
    private final String grantType = "workload_identity_federation";

    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CLIENT_ASSERTION = "client_assertion";
    private static final String PARAM_CLIENT_ASSERTION_TYPE = "client_assertion_type";
    private static final String CLIENT_ASSERTION_TYPE_JWT = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    private static final String PARAM_SCOPE = "scope";
    private static final String PARAM_GRANT_TYPE = "grant_type";

    @Getter
    protected final OAuthRefreshTokenSourceAuthConfig oauthConfig;
    @Getter
    protected final WorkloadIdentityFederationGrantConfig wifConfig;

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
                OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT,
                ConfigProperty.CLIENT_ID,
                ConfigProperty.TOKEN_SCOPE
        );
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }

    @SneakyThrows
    public HttpContent buildPayload() {
        String oauthClientId = oauthConfig.getClientId().orElseThrow(() -> new IllegalStateException(
            "Missing required config: " + OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID));

        Map<String, String> data = new TreeMap<>();
        data.put(PARAM_GRANT_TYPE, "client_credentials");
        wifConfig.getTokenScope()
                .ifPresent(r -> data.put(PARAM_SCOPE, r));

        data.put(PARAM_CLIENT_ASSERTION_TYPE, CLIENT_ASSERTION_TYPE_JWT);
        data.put(PARAM_CLIENT_ASSERTION, getClientAssertion());
        data.put(PARAM_CLIENT_ID, oauthClientId);

        return new UrlEncodedContent(data);
    }

    protected abstract String getClientAssertion();
}
