package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import com.google.api.client.http.GenericUrl;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleMeetTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GOOGLE_MEET;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/meet";

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
            sanitizer.sanitize(new GenericUrl("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/meet"), jsonString);

        assertPseudonymized(sanitized, PII);
    }
}
