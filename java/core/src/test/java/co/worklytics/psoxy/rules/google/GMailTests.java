package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GMailTests extends RulesBaseTestCase {


    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GMAIL;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/gmail";

    @Getter
    final String defaultScopeId = "gapps";

    @Test
    @SneakyThrows
    public void sanitizer() {
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

        String sanitized = sanitizer.sanitize(new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/17c3b1911726ef3f\\?format=metadata"), jsonString);

        //email address should disappear
        assertFalse(sanitized.contains(jsonPart));
        assertFalse(sanitized.contains(jsonPart.replaceAll("\\s", "")));
        assertPseudonymized(sanitized, PII);

        //redaction should remove 'Subject' header entirely; and NOT just replace it with `null`
        assertRedacted(sanitized, "Subject");
        assertFalse(sanitized.contains("null"));
    }
}
