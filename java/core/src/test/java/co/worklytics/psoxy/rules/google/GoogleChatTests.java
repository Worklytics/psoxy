package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

class GoogleChatTests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.GOOGLE_CHAT;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("google-workspace")
        .defaultScopeId("gapps")
        .sourceKind("google-chat")
        .build();


    @SneakyThrows
    @Test
    void google_chat() {
        String endpoint = "https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/chat";
        String jsonString = asJson("chat-activities.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co",
            "charlie@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize("GET", new URL(endpoint), jsonString);

        assertPseudonymized(sanitized, PII);

        assertUrlWithQueryParamsAllowed(endpoint);
        assertUrlBlocked("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/meet");
    }
}
