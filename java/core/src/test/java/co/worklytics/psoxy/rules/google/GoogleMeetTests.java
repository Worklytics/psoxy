package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

class GoogleMeetTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GOOGLE_MEET;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/meet";

    @Getter
    final String defaultScopeId = "gapps";


    @Getter
    final String yamlSerializationFilepath = "google-workspace/google-meet";

    @SneakyThrows
    @Test
    void activities() {
        String jsonString = asJson("meet-activities.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co",
            "john@worklytics.co",
            "bob@worklytics.co",
            "2611:630:924b:fd2f:ed2a:782b:e5dd:232c"
         );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/meet"), jsonString);

        assertPseudonymized(sanitized, PII);

        assertNotSanitized(jsonString, "Adam Jones");
        assertRedacted(sanitized, "Adam Jones");
    }
}
