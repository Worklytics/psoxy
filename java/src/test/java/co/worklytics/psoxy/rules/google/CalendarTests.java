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

class CalendarTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GCAL;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/calendar";

    @Test
    void events() {
        String jsonString = asJson("events.json");

        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://calendar.googleapis.com/calendar/v3/calendars/primary/events"), jsonString);

        assertPseudonymized(sanitized, PII);
    }
}
