package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesTest;
import com.google.api.client.http.GenericUrl;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoogleChatTests extends RulesTest {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GOOGLE_CHAT;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/google-chat";

    @Test
    void google_chat() {
        String jsonString = asJson("chat-activities.json");

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("philip@worklytics.co"));

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/chat"), jsonString);

        assertFalse(sanitized.contains("philip@worklytics.co"));
    }
}
