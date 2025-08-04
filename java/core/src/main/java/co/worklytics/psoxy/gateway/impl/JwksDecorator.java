package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.auth.JwtAuthorizedResource;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient.PublicKeyVersionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.http.HttpStatus;

import javax.inject.Inject;
import java.math.BigInteger;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * decorates an implementation of {@link JwtAuthorizedResource} to provide a JWKS/OIDC config endpoint.
 */
@Log
public class JwksDecorator {

    final JwtAuthorizedResource jwtAuthorizedResource;

    @Inject
    ObjectMapper objectMapper;

    @AssistedInject
    public JwksDecorator(@Assisted JwtAuthorizedResource jwtAuthorizedResource) {
        this.jwtAuthorizedResource = jwtAuthorizedResource;
    }

    // just for reference,
    public static final String PATH_TO_RESOURCE = ".well-known";

    static final String JWKS_PATH = "/jwks.json";
    static final String OPENID_CONFIG_PATH = "/openid-configuration";

    @SneakyThrows
    public HttpEventResponse handle(HttpEventRequest request) {
        Object content = null;
        try {
            if (request.getPath().endsWith(JWKS_PATH)) {
                content = serveJwks();
            } else if (request.getPath().endsWith(OPENID_CONFIG_PATH)) {
                content = serveOpenIdConfig();
            } else {
                return HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .header("Content-Type", "application/json")
                .body("{\"error\": \"Invalid request path\"}")
                .build();
            }
        } catch (Exception e) {
            log.severe("Error serving JWKS or OpenID config: " + e.getMessage());
            return HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                .header("Content-Type", "application/json")
                .body("{\"error\": \"Internal Server Error\"}")
                .build();
        }

        return HttpEventResponse.builder()
            .statusCode(HttpStatus.SC_OK)
            .header("Content-Type", "application/json")
            .body(objectMapper.writeValueAsString(content))
            .build();
    }

    JWKSResponse serveJwks() {
        Map<PublicKeyVersionId, RSAPublicKey> keys = jwtAuthorizedResource.acceptableAuthKeys();
        return new JWKSResponse(
            keys.entrySet().stream()
                    .map(entry -> JWK.fromRSAPublicKey(entry.getKey().toString(), entry.getValue()))
                    .collect(Collectors.toList())
        );
    }

     OpenIdConfig serveOpenIdConfig() {
        return new OpenIdConfig(
            jwtAuthorizedResource.getIssuer(),
            jwtAuthorizedResource.getIssuer() + "/" + PATH_TO_RESOURCE + JWKS_PATH
        );
    }

    static class OpenIdConfig {
        public final String issuer;
        public final String jwks_uri;

        public OpenIdConfig(String issuer, String jwksUri) {
            this.issuer = issuer;
            this.jwks_uri = jwksUri;
        }
    }

    // DTOs for JWKS
    static class JWKSResponse {
        public final java.util.List<JWK> keys;
        public JWKSResponse(java.util.List<JWK> keys) {
            this.keys = keys;
        }
    }

    static class JWK {
        public final String kty = "RSA";
        public final String alg = "RS256";
        public final String use = "sig";
        public final String kid;
        public final String n;
        public final String e;

        public JWK(String kid, String n, String e) {
            this.kid = kid;
            this.n = n;
            this.e = e;
        }

        public static JWK fromRSAPublicKey(String id, RSAPublicKey key) {
            String n = base64UrlEncode(key.getModulus());
            String e = base64UrlEncode(key.getPublicExponent());
            return new JWK(id, n, e);
        }

        private static String base64UrlEncode(BigInteger value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray());
        }
    }

    @AssistedFactory
    public interface Factory {
        JwksDecorator create(JwtAuthorizedResource jwtAuthorizedResource);
    }
}
