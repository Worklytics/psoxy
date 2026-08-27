package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Stream;

//TODO: fix this re-use via inheritance; makes tests brittle; we should inject this rule set into
// the directory tests, or something like that
public class CalendarTests extends EntraIDTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_CALENDAR;

    static final Collection<String> EVENT_FIELDS_TO_REDACT = Set.of(
        "transactionId",
        "reminderMinutesBeforeStart",
        "isReminderOn",
        "allowNewTimeProposals"
    );

    @Getter
    RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("microsoft-365")
        .defaultScopeId("azure-ad")
        .sourceKind("outlook-cal")
        .checkUncompressedSSMLength(false)
        .build();

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    void events(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events";

        String jsonResponse = asJson("Events_" + apiVersion + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting" //body
        );

        assertPseudonymized(sanitized,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com"
        );

        assertNotSanitized(jsonResponse, EVENT_FIELDS_TO_REDACT);
        assertRedacted(sanitized, EVENT_FIELDS_TO_REDACT);

    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    void calendarEvents(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendars/AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEGAAAiIsqMbYjsT5e-T7KzowPTAAABuC35AAA=/events";

        String jsonResponse = asJson("CalendarEvents_" + apiVersion + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting" //body
        );

        assertPseudonymized(sanitized,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com"
        );

        assertNotSanitized(jsonResponse, EVENT_FIELDS_TO_REDACT);
        assertRedacted(sanitized, EVENT_FIELDS_TO_REDACT);

    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    void calendarViews(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView";

        assertUrlAllowed(endpoint);
        assertUrlWithQueryParamsAllowed(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);

        String jsonResponse = asJson("CalendarView_" + apiVersion + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting" //body
        );

        assertPseudonymized(sanitized,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com"
        );

        assertNotSanitized(jsonResponse, EVENT_FIELDS_TO_REDACT);
        assertRedacted(sanitized, EVENT_FIELDS_TO_REDACT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    void calendarViewsWithOData(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
                "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView?startDateTime=2020-01-01T00%3a00%3a00Z&endDateTime=2024-09-07T00%3a00%3a00Z&%24top=100&%24skip=100";

        assertUrlAllowed(endpoint);
        assertUrlWithQueryParamsAllowed(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);

        String jsonResponse = asJson("CalendarView_" + apiVersion + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
                "Irvin Sayers",
                "New Product Regulations Touchpoint", //subject
                "New Product Regulations Strategy Online Touchpoint Meeting" //body
        );

        assertPseudonymized(sanitized,
                "engineering@M365x214355.onmicrosoft.com",
                "IrvinS@M365x214355.onmicrosoft.com"
        );

        assertNotSanitized(jsonResponse, EVENT_FIELDS_TO_REDACT);
        assertRedacted(sanitized, EVENT_FIELDS_TO_REDACT);

    }


    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    void event(String apiVersion) {
        String eventId = "AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAENAAAiIsqMbYjsT5e-T7KzowPTAAAa_WKzAAA=";
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events/" + eventId;

        String jsonResponse = asJson("Event_" + apiVersion + ".json");

        assertNotSanitized(jsonResponse,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com",
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting" //body
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        // a bare single Event object (not `value[]`-wrapped) -- handled by GetEventResponse,
        // whose pathRegex is disjoint from the collection endpoint's.
        assertPseudonymized(sanitized, "engineering@M365x214355.onmicrosoft.com");
        assertPseudonymized(sanitized, "IrvinS@M365x214355.onmicrosoft.com");
        assertRedacted(sanitized,
            "Irvin Sayers",
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting" //body
            );

        assertNotSanitized(jsonResponse, EVENT_FIELDS_TO_REDACT);
        assertRedacted(sanitized, EVENT_FIELDS_TO_REDACT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    public void mailboxSettings(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/mailboxSettings";

        assertUrlAllowed(endpoint);
        assertUrlWithQueryParamsAllowed(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);
    }


    @Test
    public void calendarView_zoomUrls() {
        String endpoint = "https://graph.microsoft.com/" + "v1.0" +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView";

        String jsonResponse = asJson("CalendarView_v1.0_wZoomUrls.json");

        assertNotSanitized(jsonResponse,
            "https://acme.zoom.us/j/12354234234?pwd=123123&from=addon"
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized, "pwd=123123");

        assertNotSanitized(sanitized, "https://acme.zoom.us/j/12354234234");

        assertNotSanitized(jsonResponse, EVENT_FIELDS_TO_REDACT);
        assertRedacted(sanitized, EVENT_FIELDS_TO_REDACT);
    }

    @Test
    public void calendarView_teams_meeting() {
        String endpoint = "https://graph.microsoft.com/" + "v1.0" +
                "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView";

        String jsonResponse = asJson("CalendarView_v1.0_wOnlineMeetings.json");

        assertNotSanitized(jsonResponse,
                "https://teams.microsoft.com/l/meetup-join/19%3ameeting_MjI3MDU2NWItYTdmYy00YTRiLTkyOGQtNzE1OTQ4NDBkZDEz%40thread.v2/0?context=%7b%22Tid%22%3a%226e4c8e9f-76cf-41d1-806e-61838b880b87%22%2c%22Oid%22%3a%226257b47d-9e87-418b-9ac2-031f09397de7%22%7d"
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        assertNotSanitized(sanitized, "https://teams.microsoft.com/l/meetup-join/19%3ameeting_MjI3MDU2NWItYTdmYy00YTRiLTkyOGQtNzE1OTQ4NDBkZDEz%40thread.v2/0?context=%7b%22Tid%22%3a%226e4c8e9f-76cf-41d1-806e-61838b880b87%22%2c%22Oid%22%3a%226257b47d-9e87-418b-9ac2-031f09397de7%22%7d");
    }

    @Override // rather than copy directory examples
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendars/AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEGAAAiIsqMbYjsT5e-T7KzowPTAAABuC35AAA=/events", "CalendarEvents_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView",
                "CalendarView_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView",
                "CalendarView_v1.0_wZoomUrls.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView?startDateTime=2020-01-01T00%3a00%3a00Z&endDateTime=2024-09-07T00%3a00%3a00Z&%24top=100&%24skip=100",
                        "CalendarView_v1.0_wZoomUrls.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView",
                        "CalendarView_v1.0_wOnlineMeetings.json"),
            //InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView",
            //    "CalendarView_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events",
                "Events_v1.0.json"),
            // events - allowedQueryParams is "*" (unrestricted); proves an arbitrary/unlisted param is still allowed
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events?arbitraryTestParam=shouldBeAllowed",
                "Events_v1.0.json"),
            // calendarView - allowedQueryParams is "*" (unrestricted); proves an arbitrary/unlisted param is still allowed
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView?arbitraryTestParam=shouldBeAllowed",
                "CalendarView_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events/asdfasdf",
                "Event_v1.0.json"),
            // events/{id} - allowedQueryParams is "*" (unrestricted); proves an arbitrary/unlisted param is still allowed
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events/asdfasdf?arbitraryTestParam=shouldBeAllowed",
                "Event_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315", "group.json"),
            // groups/{id} - allowedQueryParams is "*" (unrestricted); proves an arbitrary/unlisted param is still allowed
            InvocationExample.of("https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315?arbitraryTestParam=shouldBeAllowed", "group.json"),
            // users/{id} - with all allowed query params
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038?$select=id,mail", "user.json")
            );
    }
}
