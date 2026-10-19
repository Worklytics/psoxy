package co.worklytics.psoxy.gateway.impl.oauth;

import com.google.api.client.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Singleton
public class AuthUtils {

    /**
     * RFC 7523 JWT bearer grant. Google's token endpoint uses this when exchanging a signed
     * service-account JWT (including domain-wide delegation). Distinct from the client-assertion
     * type {@code urn:ietf:params:oauth:client-assertion-type:jwt-bearer}.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523#section-2.1">RFC 7523 §2.1</a>
     */
    public static final String JWT_BEARER_GRANT_TYPE =
        "urn:ietf:params:oauth:grant-type:jwt-bearer";

    /** OAuth 2.0 token-request form field; RFC 6749. */
    public static final String PARAM_GRANT_TYPE = "grant_type";

    /**
     * RFC 7523 JWT bearer token-request form field for the signed JWT.
     *
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7523#section-2.1">RFC 7523 §2.1</a>
     */
    public static final String PARAM_ASSERTION = "assertion";

    /**
     * Sets Basic Auth header on given HttpHeaders
     */
    public static void setBasicAuthHeader(
        HttpHeaders headers,
        String clientId,
        String clientSecret) {
        String token = Base64.getEncoder()
            .encodeToString(String.join(":", clientId, clientSecret).getBytes(StandardCharsets.UTF_8));

        headers.setAuthorization("Basic " + token);
    }
}
