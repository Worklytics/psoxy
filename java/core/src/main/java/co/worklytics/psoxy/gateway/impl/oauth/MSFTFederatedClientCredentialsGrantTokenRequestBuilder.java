package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.RequiresConfiguration;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.UrlEncodedContent;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.inject.Inject;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * implementation of https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-client-creds-grant-flow#third-case-access-token-request-with-a-federated-credential
 * <p>
 * see
 * - https://datatracker.ietf.org/doc/html/rfc7521
 * - https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2
 */
public abstract class MSFTFederatedClientCredentialsGrantTokenRequestBuilder
        implements OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder, RequiresConfiguration {

    enum ConfigProperty implements ConfigService.ConfigProperty {
        TENANT_ID,
        CLIENT_ID,
        TOKEN_SCOPE,
    }

    // 'client_credentials' is MSFT
    //for Google, this is "urn:ietf:params:oauth:grant-type:jwt-bearer"
    @Getter(onMethod_ = @Override)
    private final String grantType = "msft_federated_client_credentials";

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

    @Inject
    ConfigService config;

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
                OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT,
                ConfigProperty.CLIENT_ID,
                ConfigProperty.TENANT_ID,
                ConfigProperty.TOKEN_SCOPE
        );
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }

    @SneakyThrows
    public HttpContent buildPayload() {

        //implementation of:
        // https://learn.microsoft.com/en-us/azure/active-directory/develop/v2-oauth2-client-creds-grant-flow#third-case-access-token-request-with-a-federated-credential

        String oauthClientId = config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID);
        String tokenEndpoint =
                config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT);

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