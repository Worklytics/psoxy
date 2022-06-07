package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.Rules1;
import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GMailTests extends JavaRulesTestBaseCase {


    @Getter
    final Rules1 rulesUnderTest = PrebuiltSanitizerRules.GMAIL;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/gmail";

    @Getter
    final String defaultScopeId = "gapps";

    @Getter
    final String yamlSerializationFilepath = "google-workspace/gmail";

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
            "alice@worklytics.co",
            "lois@worklytics.co",
            "karen@worklytics.co",
            "john@worklytics.co",
            "mike@worklytics.co"
        );
        Collection<String> names = Arrays.asList(
            "Karen Love",
            "John Doe",
            "Mike Smith"
        );
        Collection<String> someAllowedHeaders = Arrays.asList(
            "Delivered-To",
            "From",
            "To",
            "Cc",
            "bcC" // weird pattern to check case insensitivity
        );
        Collection<String> someHeadersToDrop = Arrays.asList(
            "ARC-Seal",
            "Received-SPF",
            "DKIM-Signature"
        );
        assertNotSanitized(jsonString, PII);
        assertNotSanitized(jsonString, names);
        assertNotSanitized(jsonString, someHeadersToDrop);
        // basically assert contains the allowed headers
        assertNotSanitized(jsonString, someAllowedHeaders);

        String sanitized = sanitizer.sanitize(new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/17c3b1911726ef3f?format=metadata"), jsonString);

        // names should be dropped
        assertRedacted(sanitized, names);
        // headers not allowed should be dropped
        assertRedacted(sanitized, someHeadersToDrop);

        //email address should disappear
        assertFalse(sanitized.contains(jsonPart));
        assertFalse(sanitized.contains(jsonPart.replaceAll("\\s", "")));
        assertPseudonymized(sanitized, PII);

        //redaction should remove 'Subject' header entirely; and NOT just replace it with `null`
        assertRedacted(sanitized, "Subject");
        assertFalse(sanitized.contains("null"));

        assertTrue(sanitized.contains("Message-ID"));
    }

    @Test
    public void messagesEndpointWithQueryParamsAllowed() {
        //need paging, etc to work ...
        String messagesEndpoint = "https://gmail.googleapis.com/gmail/v1/users/me/messages";
        assertUrlWithQueryParamsAllowed(messagesEndpoint);
    }
}
