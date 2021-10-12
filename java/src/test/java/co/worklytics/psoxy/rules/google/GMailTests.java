package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.RulesTest;
import co.worklytics.test.TestUtils;
import com.google.api.client.http.GenericUrl;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GMailTests extends RulesTest {


    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GMAIL;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/gmail";

    @Test
    void sanitize() {

        String jsonPart = "{\n" +
            "        \"name\": \"To\",\n" +
            "        \"value\": \"ops@worklytics.co\"\n" +
            "      }";

        String jsonString = asJson("message.json");

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains(jsonPart));
        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = sanitizer.sanitize(new GenericUrl("https://gmail.googleapis.com/gmail/v1/users/me/messages/17c3b1911726ef3f\\?format=metadata"), jsonString);

        //email address should disappear
        assertFalse(sanitized.contains(jsonPart));
        assertFalse(sanitized.contains(jsonPart.replaceAll("\\s", "")));
        assertPseudonymized(sanitized, PII);

        //redaction should remove 'Subject' header entirely; and NOT just replace it with `null`
        assertRedacted(sanitized, "Subject");
        assertFalse(sanitized.contains("null"));
    }
}
