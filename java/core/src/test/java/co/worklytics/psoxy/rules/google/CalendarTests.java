package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;


class CalendarTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GCAL;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/calendar";

    @Getter
    final String defaultScopeId = "gapps";

    @SneakyThrows
    @Test
    void events_privacy() {
        String jsonString = asJson("events.json");

        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);
        assertNotSanitized(jsonString, "calendar-owner@acme.com");

        String sanitized =
            sanitizer.sanitize(new URL("http://calendar.googleapis.com/calendar/v3/calendars/primary/events"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "calendar-owner@acme.com");
    }

    @SneakyThrows
    @Test
    void events_confidentiality() {
        String jsonString = asJson("events.json");

        assertNotSanitized(jsonString, "Call to discuss Worklytics issues");
        assertNotSanitized(jsonString, "Dear alice :");

        String sanitized =
            sanitizer.sanitize(new URL("http://calendar.googleapis.com/calendar/v3/calendars/primary/events"), jsonString);

        assertRedacted(sanitized, "Call to discuss Worklytics issues");
        assertRedacted(sanitized, "Dear alice :");
    }
}
