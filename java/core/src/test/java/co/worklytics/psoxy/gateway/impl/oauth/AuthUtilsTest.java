package co.worklytics.psoxy.gateway.impl.oauth;

import com.google.api.client.http.HttpHeaders;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class AuthUtilsTest {

    @Test
    void describeJwtForLogging_decodesHeaderAndPayloadWithoutSignature() {
        String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"iss\":\"client-id\",\"aud\":\"https://login.microsoftonline.com/tenant/oauth2/v2.0/token\"}");
        String jwt = header + "." + payload + ".signature-bytes";

        String described = AuthUtils.describeJwtForLogging(jwt);

        assertTrue(described.contains("\"alg\":\"RS256\""));
        assertTrue(described.contains("\"iss\":\"client-id\""));
        assertTrue(described.contains("signaturePresent=true"));
        assertFalse(described.contains("signature-bytes"));
    }

    @Test
    void describeJwtForLogging_handlesBlankAndNonJwt() {
        assertEquals("(blank)", AuthUtils.describeJwtForLogging(" "));
        assertTrue(AuthUtils.describeJwtForLogging("not-a-jwt").startsWith("not-a-jwt"));
    }

    @Test
    void describeHeadersForLogging_includesMsftDiagnostics() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-ms-request-id", "req-123");
        headers.set("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        headers.set("X-Ignored", "nope");

        String described = AuthUtils.describeHeadersForLogging(headers);

        assertTrue(described.contains("x-ms-request-id=req-123"));
        assertTrue(described.contains("WWW-Authenticate=Bearer error=\"invalid_token\""));
        assertFalse(described.contains("X-Ignored"));
    }

    @Test
    void nullSafeTrim_handlesNullAndWhitespace() {
        assertEquals("(null body)", AuthUtils.nullSafeTrim(null));
        assertEquals("short", AuthUtils.nullSafeTrim(" short "));
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
