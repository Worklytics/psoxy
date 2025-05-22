
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
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("google-workspace")
        .defaultScopeId("gapps")
        .sourceKind("calendar")
        .checkUncompressedSSMLength(false) //~13 kB now
        .build();

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
            sanitizer.sanitize("GET", new URL("http://calendar.googleapis.com/calendar/v3/calendars/primary/events"), jsonString);

        assertPseudonymized(sanitized, PII);

        assertUrlAllowed("http://calendar.googleapis.com/calendar/v3/calendars/primary/events?maxResults=200");
        assertUrlAllowed("http://calendar.googleapis.com/calendar/v3/calendars/primary/events?maxResults=200&pageToken=1234");

        assertRedacted(sanitized, "calendar-owner@acme.com");
    }

    @SneakyThrows
    @Test
    void events_confidentiality() {
        String jsonString = asJson("events.json");

        assertNotSanitized(jsonString, "Call to discuss Worklytics issues");
        assertNotSanitized(jsonString, "Dear alice :");

        String sanitized =
            sanitizer.sanitize("GET", new URL("http://calendar.googleapis.com/calendar/v3/calendars/primary/events"), jsonString);

        assertRedacted(sanitized, "Call to discuss Worklytics issues");
        assertRedacted(sanitized, "Dear alice :");
    }

    @SneakyThrows
    @Test
    void calendarList() {
        assertUrlWithSubResourcesBlocked("https://www.googleapis.com/calendar/v3/users/me/calendarList");
        assertUrlAllowed("https://www.googleapis.com/calendar/v3/users/me/calendarList?");
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
                InvocationExample.of("http://calendar.googleapis.com/calendar/v3/calendars/primary/events", "events.json"),
                InvocationExample.of("http://calendar.googleapis.com/calendar/v3/calendars/primary/events/1234324", "event.json"),
                InvocationExample.of("https://www.googleapis.com/calendar/v3/users/me/calendarList", "calendarList.json")
        );
    }
}
