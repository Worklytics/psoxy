package co.worklytics.psoxy.gateway.impl.oauth;

import com.google.api.client.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class AuthUtils {

    /**
     * Response / request header names useful when diagnosing OAuth token endpoint failures
     * (especially Microsoft Entra / login.microsoftonline.com).
     */
    static final String[] TOKEN_ERROR_HEADER_NAMES = {
        "WWW-Authenticate",
        "x-ms-request-id",
        "x-ms-ests-server",
        "x-ms-error-code",
        "x-ms-cltid",
        "client-request-id",
        "request-id",
        "Date",
        "Content-Type",
    };

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

    /**
     * Decode a JWT's header and payload for logging. Signature is omitted so the token cannot be
     * replayed from logs.
     *
     * @param jwt compact serialized JWT (header.payload.signature)
     * @return human-readable description, or a note if the value is not a JWT
     */
    public static String describeJwtForLogging(String jwt) {
        if (StringUtils.isBlank(jwt)) {
            return "(blank)";
        }

        String[] parts = StringUtils.split(jwt, '.');
        if (parts == null || parts.length < 2) {
            return "not-a-jwt length=" + jwt.length();
        }

        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(padBase64Url(parts[0])), StandardCharsets.UTF_8);
            String payloadJson = new String(Base64.getUrlDecoder().decode(padBase64Url(parts[1])), StandardCharsets.UTF_8);
            return "header=" + headerJson + " payload=" + payloadJson
                + " signaturePresent=" + (parts.length >= 3 && StringUtils.isNotBlank(parts[2]));
        } catch (IllegalArgumentException e) {
            return "unparseable-jwt length=" + jwt.length() + " (" + e.getMessage() + ")";
        }
    }

    /**
     * Format selected HTTP headers for token-error diagnostics.
     */
    public static String describeHeadersForLogging(HttpHeaders headers) {
        if (headers == null) {
            return "(none)";
        }

        Map<String, String> interesting = new LinkedHashMap<>();
        for (String name : TOKEN_ERROR_HEADER_NAMES) {
            String value = headers.getFirstHeaderStringValue(name);
            if (StringUtils.isNotBlank(value)) {
                interesting.put(name, value);
            }
        }

        if (interesting.isEmpty()) {
            return "(no diagnostic headers present)";
        }

        return interesting.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .collect(Collectors.joining(", "));
    }

    /**
     * Normalize a response body for logs. Token-endpoint error bodies (especially Entra) are small
     * enough that we keep the full body so AADSTS text / correlation ids are never clipped.
     */
    public static String nullSafeTrim(String body) {
        if (body == null) {
            return "(null body)";
        }
        return body.trim();
    }

    private static String padBase64Url(String value) {
        int mod = value.length() % 4;
        if (mod == 0) {
            return value;
        }
        return value + "====".substring(mod);
    }
}
