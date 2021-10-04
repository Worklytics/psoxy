package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Sanitizer;
import co.worklytics.test.TestUtils;
import com.google.api.client.http.GenericUrl;
import com.jayway.jsonpath.Configuration;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class SanitizerImplTest {

    @Test
    void sanitize_poc() {
        SanitizerImpl sanitizer = new SanitizerImpl(Sanitizer.Options.builder()
            .pseudonymization(Pair.of("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*",
                Arrays.asList(
                    "$.payload.headers[?(@.name in ['To','TO','to','From','FROM','from','cc','CC','bcc','BCC'])].value"
                )))
            .build());

        String jsonPart = "{\n" +
            "        \"name\": \"To\",\n" +
            "        \"value\": \"ops@worklytics.co\"\n" +
            "      }";

        String jsonString = new String(TestUtils.getData("api-response-examples/g-workspace/gmail/message.json"));

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains(jsonPart));

        String sanitized = sanitizer.sanitize(new GenericUrl("https://gmail.googleapis.com/gmail/v1/users/me/messages/17c3b1911726ef3f\\?format=metadata"), jsonString);

        assertFalse(sanitized.contains(jsonPart));
        assertFalse(sanitized.contains(jsonPart.replaceAll("\\s","")));
    }

    @Test
    void emailDomains() {
        SanitizerImpl sanitizer = new SanitizerImpl(Sanitizer.Options.builder()
            .pseudonymization(Pair.of("\\/gmail\\/v1\\/users\\/.*?\\/messages\\/.*",
                Arrays.asList(
                    "$.payload.headers[?(@.name in ['To','TO','to','From','FROM','from','cc','CC','bcc','BCC'])].value"
                )))
            .pseudonymizationSalt("salt")
            .build());

        assertEquals("worklytics.co",
            sanitizer.pseudonymize("alice@worklytics.co").getDomain());
        assertEquals("worklytics.co",
            sanitizer.pseudonymize("Alice Example <alice@worklytics.co>").getDomain());
        assertEquals("worklytics.co",
            sanitizer.pseudonymize("\"Alice Example\" <alice@worklytics.co>").getDomain());
        assertEquals("worklytics.co",
            sanitizer.pseudonymize("Alice.Example@worklytics.co").getDomain());
    }
}
