package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

class GoogleChatTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GOOGLE_CHAT;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/google-chat";

    @Getter
    final String defaultScopeId = "gapps";


    @SneakyThrows
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
            sanitizer.sanitize(new URL("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/chat"), jsonString);

        assertPseudonymized(sanitized, PII);
    }
}
