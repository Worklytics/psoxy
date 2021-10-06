package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.PrebuiltSanitizerRules;
import co.worklytics.psoxy.Pseudonym;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.test.TestUtils;
import com.google.api.client.http.GenericUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SanitizerImplTest {

    static final String ALICE_CANONICAL = "alice@worklytics.co";

    SanitizerImpl sanitizer;

    @BeforeEach
    public void setup() {
        sanitizer = new SanitizerImpl(Sanitizer.Options.builder()
            .rules(PrebuiltSanitizerRules.MAP.get("gmail"))
            .pseudonymizationSalt("salt")
            .build());
    }

    @Test
    void sanitize_poc() {

        String jsonPart = "{\n" +
            "        \"name\": \"To\",\n" +
            "        \"value\": \"ops@worklytics.co\"\n" +
            "      }";

        String jsonString = new String(TestUtils.getData("api-response-examples/g-workspace/gmail/message.json"));

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains(jsonPart));
        assertTrue(jsonString.contains("erik@worklytics.co"));
        assertTrue(jsonString.contains("Subject"));

        String sanitized = sanitizer.sanitize(new GenericUrl("https://gmail.googleapis.com/gmail/v1/users/me/messages/17c3b1911726ef3f\\?format=metadata"), jsonString);

        assertFalse(sanitized.contains(jsonPart));
        assertFalse(sanitized.contains(jsonPart.replaceAll("\\s","")));
        assertFalse(sanitized.contains("erik@worklytics.co"));
        assertFalse(sanitized.contains("Subject"));

    }

    @ValueSource(strings = {
        "alice@worklytics.co",
        "Alice Example <alice@worklytics.co>",
        "\"Alice Example\" <alice@worklytics.co>",
        "Alice.Example@worklytics.co"
    })
    @ParameterizedTest
    void emailDomains(String mailHeaderValue) {
        assertEquals("worklytics.co", sanitizer.pseudonymize(mailHeaderValue).getDomain());
    }

    @ValueSource(strings = {
        ALICE_CANONICAL,
        "Alice Example <alice@worklytics.co>",
        "\"Alice Different Last name\" <alice@worklytics.co>",
        "Alice@worklytics.co",
        "AlIcE@worklytics.co",
    })
    @ParameterizedTest
    void emailCanonicalEquivalents(String mailHeaderValue) {
        Pseudonym canonicalExample = sanitizer.pseudonymize(ALICE_CANONICAL);

        assertEquals(canonicalExample.getHash(),
            sanitizer.pseudonymize(mailHeaderValue).getHash());
    }

    @ValueSource(strings = {
        "bob@worklytics.co",
        "Alice Example <alice2@worklytics.co>",
        "\"Alice Example\" <alice-a@worklytics.co>",
        "Alice@somewhere-else.co",
        "AlIcE.Other@worklytics.co",
    })
    @ParameterizedTest
    void emailCanonicalDistinct(String mailHeaderValue) {
        Pseudonym canonicalExample = sanitizer.pseudonymize(ALICE_CANONICAL);

        assertNotEquals(canonicalExample.getHash(),
            sanitizer.pseudonymize(mailHeaderValue).getHash());
    }
}
