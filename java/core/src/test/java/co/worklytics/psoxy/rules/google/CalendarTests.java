
package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;


class CalendarTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.GCAL;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/calendar";

    @Getter
    final String defaultScopeId = "gapps";


    @Getter
    final String yamlSerializationFilepath = "google-workspace/calendar";

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

        assertUrlWithQueryParamsAllowed("http://calendar.googleapis.com/calendar/v3/calendars/primary/events");

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

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
                InvocationExample.of("http://calendar.googleapis.com/calendar/v3/calendars/primary/events", "events.json")
            );
    }
}
