package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.auth.JwtAuthorizedResource;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * decorates an implementation of {@link JwtAuthorizedResource} to provide a JWKS endpoint.
 */
public class JwksDecorator {

    JwtAuthorizedResource jwtAuthorizedResource;

    public JwksDecorator(JwtAuthorizedResource jwtAuthorizedResource) {
        this.jwtAuthorizedResource = jwtAuthorizedResource;
    }

    public HttpEventResponse handle(HttpEventRequest request) {
        if (!request.getPath().endsWith(".well-known/jwks.json")) {
            throw new IllegalArgumentException("Invalid JWKS request path: " + request.getPath());
        }
        try {
            Collection<RSAPublicKey> keys = jwtAuthorizedResource.acceptableAuthKeys();
            JWKSResponse jwks = new JWKSResponse(
                keys.stream().map(JWK::fromRSAPublicKey).collect(Collectors.toList())
            );
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(jwks);
            return HttpEventResponse.builder()
                .statusCode(200)
                .header("Content-Type", "application/json")
                .body(json)
                .build();
        } catch (Exception e) {
            return HttpEventResponse.builder()
                .statusCode(500)
                .body("{\"error\":\"Failed to generate JWKS: " + e.getMessage() + "\"}")
                .build();
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

        public static JWK fromRSAPublicKey(RSAPublicKey key) {
            String kid = Integer.toHexString(key.hashCode()); // or use a better key id
            String n = base64UrlEncode(key.getModulus());
            String e = base64UrlEncode(key.getPublicExponent());
            return new JWK(kid, n, e);
        }

        private static String base64UrlEncode(BigInteger value) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray());
        }
    }
}
