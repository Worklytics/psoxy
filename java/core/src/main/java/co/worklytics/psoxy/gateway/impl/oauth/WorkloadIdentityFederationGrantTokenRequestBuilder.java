package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.UrlEncodedContent;
import lombok.Getter;
import lombok.SneakyThrows;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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


    protected WorkloadIdentityFederationGrantTokenRequestBuilder(ConfigService configService) {
        this.config = configService;
    }

    @Getter
    protected enum ConfigProperty implements ConfigService.ConfigProperty {
        CLIENT_ID,
        TOKEN_SCOPE,
        ;

        private final boolean envVarOnly = true;
    }

    // as far as what's actually sent in the OAuth 2.0 'grant_type' param:
    //  - 'client_credentials' is MSFT
    //  - for Google, this is "urn:ietf:params:oauth:grant-type:jwt-bearer"
    // imho, I see the argument for both cases of that.
    // arguably we should make this `grantType` value a strategy, and separately configure an
    // the OAuth 2.0 grant_type value per env vars
    @Getter(onMethod_ = @Override)
    private final String grantType = "workload_identity_federation";

    //for Google, this is "assertion"
    // see: https://datatracker.ietf.org/doc/html/rfc7521#section-4.2
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CLIENT_ASSERTION = "client_assertion";
    // see: https://datatracker.ietf.org/doc/html/rfc7521#section-4.2
    private static final String PARAM_CLIENT_ASSERTION_TYPE = "client_assertion_type";
    private static final String CLIENT_ASSERTION_TYPE_JWT = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";

    //https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2
    private static final String PARAM_SCOPE = "scope";
    private static final String PARAM_GRANT_TYPE = "grant_type";

    @Getter
    final ConfigService config;

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
        String oauthClientId = config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID);

        Map<String, String> data = new TreeMap<>();
        //https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2
        data.put(PARAM_GRANT_TYPE, "client_credentials");
        config.getConfigPropertyAsOptional(ConfigProperty.TOKEN_SCOPE)
                .ifPresent(r -> data.put(PARAM_SCOPE, r)); // 'scope' param is optional, per RFC

        //https://datatracker.ietf.org/doc/html/rfc7521#section-4.2
        data.put(PARAM_CLIENT_ASSERTION_TYPE, CLIENT_ASSERTION_TYPE_JWT);
        data.put(PARAM_CLIENT_ASSERTION, getClientAssertion());
        data.put(PARAM_CLIENT_ID, oauthClientId);

        return new UrlEncodedContent(data);
    }

    protected abstract String getClientAssertion();
}
