package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import jdk.jfr.Description;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

//TODO: fix this re-use via inheritance; makes tests brittle; we should inject this rule set into
// the directory tests, or something like that
public class CalendarTests extends EntraIDTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_CALENDAR;

    @Getter
    RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("microsoft-365")
        .defaultScopeId("azure-ad")
        .sourceKind("outlook-cal")
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

    }


    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    void event(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events";

        String jsonResponse = asJson("Event_" + apiVersion + ".json");

        assertNotSanitized(jsonResponse,
            "engineering@M365x214355.onmicrosoft.com",
            "IrvinS@M365x214355.onmicrosoft.com",
            "Irvin Sayers",
            "New Product Regulations Touchpoint", //subject
            "New Product Regulations Strategy Online Touchpoint Meeting" //body
        );

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
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    public void mailboxSettings(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion +
            "/users/48d31887-5fad-4d73-a9f5-3c356e68a038/mailboxSettings";

        assertUrlAllowed(endpoint);
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

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.ONLINE_MEETINGS_PATH_TEMPLATES)
    public void users_onlineMeetings(String apiVersion) {
        String userId = "dc17674c-81d9-4adb-bfb2-8f6a442e4622";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/users/" + userId + "/onlineMeetings";
        String jsonResponse = asJson("Users_onlineMeetings_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "everyone",
                "5552478",
                "5550588",
                "9999999",
                "https://dialin.teams.microsoft.com/6787A136-B9B8-4D39-846C-C0F1FF937F10?id=xxxxxxx",
                "153367081",
                "2018-05-30T00:12:19.0726086Z",
                "2018-05-30T01:00:00Z",
                "112f7296-5fa4-42ca-bae8-6a692b15d4b8_19:cbee7c1c860e465f8258e3cebf7bee0d@thread.skype",
                "https://teams.microsoft.com/l/meetup-join/19%3a:meeting_NTg0NmQ3NTctZDVkZC00YzRhLThmNmEtOGQDdmZDZk@thread.v2/0?context=%7b%22Tid%22%3a%aa67bd4c-8475-432d-bd41-39f255720e0a%22%2c%22Oid%22%3a%22112f7296-5fa4-42ca-bb15d4b8%22%7d",
                "112f7296-5ca-bae8-6a692b15d4b8",
                "5810cedeb-b2c1-e9bd5d53ec96",
                "joinMeetingId", "1234567890"
        );

        String sanitized = sanitize(endpoint, jsonResponse);
        assertRedacted(sanitized,
                "Tyler Stein",
                "Jasmine Miller",
                "Test Meeting.",
                "passcode",
                "isPasscodeRequired",
                "127.0.0.1",
                "macAddress",
                "reflexiveIPAddress",
                "relayIPAddress",
                "subnet"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    public void users_onlineMeetings_attendanceReports() {
        String userId = "dc17674c-81d9-4adb-bfb2-8f6a442e4622";
        String endpoint = "https://graph.microsoft.com/v1.0" + "/users/" + userId + "/onlineMeetings";
        String jsonResponse = asJson("Users_onlineMeetings_attendanceReports_v1.0.json");

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    public void users_onlineMeetings_attendanceReport() {
        String userId = "dc17674c-81d9-4adb-bfb2-8f6a442e4622";
        String endpoint = "https://graph.microsoft.com/v1.0" + "/users/" + userId + "/onlineMeetings";
        String jsonResponse = asJson("Users_onlineMeetings_attendanceReport_v1.0.json");
        assertNotSanitized(jsonResponse,
                "dc17674c-81d9-4adb-bfb2-8f6a442e4623"
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        assertPseudonymized(sanitized, "frederick.cormier@contoso.com");
        assertRedacted(sanitized,
                "Frederick Cormier",
                "frederick.cormier@contoso.com"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Override // rather than copy directory examples
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendars/AAMkAGVmMDEzMTM4LTZmYWUtNDdkNC1hMDZiLTU1OGY5OTZhYmY4OABGAAAAAAAiQ8W967B7TKBjgx9rVEURBwAiIsqMbYjsT5e-T7KzowPTAAAAAAEGAAAiIsqMbYjsT5e-T7KzowPTAAABuC35AAA=/events", "CalendarEvents_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView",
                "CalendarView_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView",
                "CalendarView_v1.0_wZoomUrls.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView",
                        "CalendarView_v1.0_wOnlineMeetings.json"),
            //InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/calendar/calendarView",
            //    "CalendarView_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events",
                "Events_v1.0.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/48d31887-5fad-4d73-a9f5-3c356e68a038/events/asdfasdf",
                "Event_v1.0.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/dc17674c-81d9-4adb-bfb2-8f6a442e4622/onlineMeetings", "Users_onlineMeetings_v1.0.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/dc17674c-81d9-4adb-bfb2-8f6a442e4622/onlineMeetings/MSpkYzE3Njc0Yy04MWQ5LTRhZGItYmZ/attendanceReports", "Users_onlineMeetings_attendanceReports_v1.0.json"),
                InvocationExample.of("https://graph.microsoft.com/v1.0/users/dc17674c-81d9-4adb-bfb2-8f6a442e4622/onlineMeetings/MSpkYzE3Njc0Yy04MWQ5LTRhZGItYmZ/attendanceReports/c9b6db1c-d5eb-427d-a5c0-20088d9b22d7?$expand=attendanceRecords", "Users_onlineMeetings_attendanceReport_v1.0.json")
            );
    }
}