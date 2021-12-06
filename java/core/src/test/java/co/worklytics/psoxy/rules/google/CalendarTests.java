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
    void events() {
        String jsonString = asJson("events.json");

        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("http://calendar.googleapis.com/calendar/v3/calendars/primary/events"), jsonString);

        assertPseudonymized(sanitized, PII);

        assertUrlWithQueryParamsAllowed("http://calendar.googleapis.com/calendar/v3/calendars/primary/events");
    }
}
