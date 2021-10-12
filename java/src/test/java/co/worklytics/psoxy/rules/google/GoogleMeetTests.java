package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesTest;
import com.google.api.client.http.GenericUrl;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleMeetTests extends RulesTest {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GOOGLE_MEET;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/meet";

    @Test
    void activities() {
        String jsonString = asJson("meet-activities.json");

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("alice@worklytics.co"));
        assertTrue(jsonString.contains("john@worklytics.co"));
        assertTrue(jsonString.contains("bob@worklytics.co"));
        assertTrue(jsonString.contains("2611:630:924b:fd2f:ed2a:782b:e5dd:232c"));

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/meet"), jsonString);

        assertFalse(sanitized.contains("alice@worklytics.co"));
        assertFalse(sanitized.contains("john@worklytics.co"));
        assertFalse(sanitized.contains("bob@worklytics.co"));
        assertFalse(sanitized.contains("2611:630:924b:fd2f:ed2a:782b:e5dd:232c"));
    }
}
