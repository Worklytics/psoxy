package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesTest;
import co.worklytics.test.TestUtils;
import com.google.api.client.http.GenericUrl;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarTests extends RulesTest {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GCAL;

    @Test
    void events() {
        String jsonString = new String(TestUtils.getData("api-response-examples/g-workspace/calendar/events.json"));

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("alice@worklytics.co"));

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://calendar.googleapis.com/calendar/v3/calendars/primary/events"), jsonString);

        assertFalse(sanitized.contains("alice@worklytics.co"));
    }
}
