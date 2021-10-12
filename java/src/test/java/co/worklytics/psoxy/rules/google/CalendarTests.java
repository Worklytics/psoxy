package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.test.TestUtils;
import com.google.api.client.http.GenericUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalendarTests {

    SanitizerImpl sanitizer;

    @BeforeEach
    public void setup() {
        sanitizer = new SanitizerImpl(Sanitizer.Options.builder()
            .rules(PrebuiltSanitizerRules.GCAL)
            .build());
    }

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
