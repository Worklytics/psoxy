package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GMailTests extends JavaRulesTestBaseCase {


    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.GMAIL;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("google-workspace")
        .defaultScopeId("gapps")
        .sourceKind("gmail")
        .build();

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

        String sanitized = sanitizer.sanitize("GET", new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/17c3b1911726ef3f?format=metadata"), jsonString);

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

    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://gmail.googleapis.com/gmail/v1/users/me/messages/sdfgsdfg", "message.json"),
            InvocationExample.of("https://gmail.googleapis.com/gmail/v1/users/me/messages/emptycc", "message_emptyCC.json")
        );
    }
}
