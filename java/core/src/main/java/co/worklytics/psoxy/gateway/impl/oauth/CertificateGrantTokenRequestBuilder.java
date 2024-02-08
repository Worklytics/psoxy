package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.UrlEncodedContent;
import com.google.api.client.json.webtoken.JsonWebSignature;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * implementation of an Access Token Request (per RFC6749 4.4.2) authenticated by an assertion (RFC 7521)
 * based on private key secret for building the JWT to use as an authentication header
 * <p>
 * see
 * - <a href="https://datatracker.ietf.org/doc/html/rfc7521">...</a>
 * - <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-4.4.2">...</a>
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class CertificateGrantTokenRequestBuilder
        extends ClientCredentialsGrantTokenRequestBuilder {

    public static final String GRANT_TYPE = "certificate_credentials";

    enum ConfigProperty implements ConfigService.ConfigProperty {
        PRIVATE_KEY
    }


    @Getter(onMethod_ = @Override)
    private final String grantType = GRANT_TYPE;

    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Set.of(
                OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT, //should be redundant with strategy, but nonetheless req'd here too
                OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID,
                ConfigProperty.PRIVATE_KEY);
    }

    @Override
    public void addHeaders(HttpHeaders httpHeaders) {
        String oauthClientId =
            secretStore.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.CLIENT_ID);
        String tokenEndpoint =
                config.getConfigPropertyOrError(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.REFRESH_ENDPOINT);

        httpHeaders.setAuthorization("Bearer " + buildJwtAssertion(oauthClientId, tokenEndpoint));
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Set.of(ConfigProperty.values());
    }

    @SneakyThrows
    @Override
    public HttpContent buildPayload() {
        return new UrlEncodedContent(Collections.emptyList());
    }

    @Override
    protected void setJWTCustomHeaders(JsonWebSignature.Header header) {
        // do nothing
    }
}
