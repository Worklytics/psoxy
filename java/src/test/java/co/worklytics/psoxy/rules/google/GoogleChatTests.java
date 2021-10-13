package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import com.google.api.client.http.GenericUrl;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

class GoogleChatTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GOOGLE_CHAT;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/google-chat";

    @Test
    void google_chat() {
        String jsonString = asJson("chat-activities.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co",
            "charlie@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/chat"), jsonString);

        assertPseudonymized(sanitized, PII);
    }
}
