package co.worklytics.psoxy.rules.zoom;

import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class ZoomRulesTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.ZOOM;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceKind("zoom")
        .build();

    @SneakyThrows
    @ValueSource(strings = {
        "https://api.zoom.us/v2/users",
        "https://api.zoom.us/v2/users?status=active&page_size=200&next_page_token=TOKEN",
        "https://api.zoom.us/v2/users/USER_ID/meetings",
        "https://api.zoom.us/v2/users/USER_ID/meetings?type=scheduled&page_size=20",
        "https://api.zoom.us/v2/past_meetings/MEETING_ID",
        "https://api.zoom.us/v2/past_meetings/MEETING_ID/instances",
        "https://api.zoom.us/v2/past_meetings/MEETING_ID/participants",
        "https://api.zoom.us/v2/past_meetings/AAAAAAAAAAAAAAAAA==/participants?page_size=1",
        "https://api.zoom.us/v2/past_meetings/AAAAAAAAAAAAAAAAA==/participants?page_size=1",
        "https://api.zoom.us/v2/past_meetings/AAAAAAAAAAAAAAAAA%253D%253D/participants?page_size=1",
        "https://api.zoom.us/v2/past_meetings/AAAA%252FAAAAAA%252F%252BAaaaaAA%253D%253D/participants?page_size=1",
        "https://api.zoom.us/v2/past_meetings/MEETING_ID/participants?page_size=20&next_page_token=TOKEN",
        "https://api.zoom.us/v2/meetings/MEETING_ID",
        "https://api.zoom.us/v2/meetings/MEETING_ID?occurence_id=OCCURRENCE_ID&show_previous_occurrences=false",
        "https://api.zoom.us/v2/report/users/{userId}/meetings",
        "https://api.zoom.us/v2/report/users/myuserid/meetings?from=2022-05-16&to=2022-05-31&type=pastJoined&page_size=1",
        "https://api.zoom.us/v2/report/meetings/{meetingId}",
        "https://api.zoom.us/v2/report/meetings/{meetingId}/participants",
        "https://api.zoom.us/v2/report/meetings/{meetingId}/participants?page_size=300",
        "https://api.zoom.us/v2/report/meetings/NUXghb123TCj0bP6nPVe%252Fsg%253D%253D/participants?page_size=300",
        "https://api.zoom.us/v2/report/meetings/NUXghb123TCj0bP6nPVe%2Fsg%3D%3D/participants?page_size=300", // url decode id once
        //"https://api.zoom.us/v2/report/meetings/NUXghb123TCj0bP6nPVe/sg==/participants?page_size=300", // url decode id twice
    })
    @ParameterizedTest
    void allowedEndpointRegex_allowed(String url) {
        assertTrue(sanitizer.isAllowed("GET", new URL(url)), url + " should be allowed");
    }

    @SneakyThrows
    @ValueSource(strings = {
        // variations on allowed
        "https://api.zoom.us/v2/users/",
        "https://api.zoom.us/v2/users/USER_ID/meetings/",
        "https://api.zoom.us/v2/past_meetings",
        "https://api.zoom.us/v2/meetings",
        // some random valid methods we don't use
        "https://api.zoom.us/v2/report/daily",
        "https://api.zoom.us/v2/meetings/{meetingId}/registrants",
        "https://api.zoom.us/v2/meetings/{meetingId}/polls",
        "https://api.zoom.us/v2/meetings/{meetingId}/polls/{pollId}",
        "https://api.zoom.us/v2/users/{userId}/meeting_templates",
        "https://api.zoom.us/v2/groups",
        "https://api.zoom.us/v2/groups/{groupId}/admins",
        "https://api.zoom.us/v2/metrics/webinars",

    })
    @ParameterizedTest
    void allowedEndpointRegex_blocked(String url) {
        assertFalse(sanitizer.isAllowed("GET", new URL(url)), url + " should be blocked");
    }

    @SneakyThrows
    @Test
    void list_users() {
        String jsonString = asJson("list-users.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "example@example.com",
            "+1 555-555-5555"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize("GET", new URL("https://api.zoom.us/v2/users"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Taylor", "Kim", "https://example.com/photo.jpg");
        assertReversibleUrlTokenized(sanitized, Arrays.asList("111111111"));
    }

    @SneakyThrows
    @Test
    void list_user_meetings() {
        String jsonString = asJson("list-user-meetings.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "abckjdfhsdkjf" // host id
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize("GET", new URL("https://api.zoom.us/v2/users/ANY_USER_ID/meetings"), jsonString);

        assertPseudonymized(sanitized, PII);
        // topics & join_urls gone
        assertRedacted(sanitized, "https://zoom.us", "Zoom Meeting", "TestMeeting", "My Meeting", "MyTestPollMeeting",
            "?pwd=1234567890",
            "SHOULD BE REDACTED"
            );
    }

    @SneakyThrows
    @Test
    void meeting_details() {
        String jsonString = asJson("meeting-details.json");

        Collection<String> PII = Arrays.asList(
            "ABcdofjdogh11111", // host id
            "james@example.com" // host email
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize("GET", new URL("https://api.zoom.us/v2/meetings/MEETING_ID?show_previous_occurrences=false"), jsonString);

        assertPseudonymized(sanitized, PII);

        assertRedacted(sanitized, "example@example.com", "API overview", "My API Test",             "?pwd=1234567890",
            "SHOULD BE REDACTED");
    }

    @SneakyThrows
    @Test
    void list_past_meeting_instances() {
        String jsonString = asJson("past-meeting-instances.json");

        String sanitized =
            sanitizer.sanitize("GET", new URL("https://api.zoom.us/v2/past_meetings/MEETING_ID/instances"), jsonString);

        // no rules here
        assertJsonEquals(jsonString, sanitized);
    }

    @SneakyThrows
    @Test
    void past_meeting_details() {
        String jsonString = asJson("past-meeting-details.json");

        Collection<String> PII = Arrays.asList(
            "DYHrdpjrS3uaOf7dPkkg8w", // host id
            "user@example.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize("GET", new URL("https://api.zoom.us/v2/past_meetings/MEETING_ID"), jsonString);

        assertPseudonymized(sanitized, PII);

        assertRedacted(sanitized, "Joe Surname", "My Meeting",            "?pwd=1234567890",
            "SHOULD BE REDACTED");
    }

    @SneakyThrows
    @Test
    void past_meeting_participants() {
        String jsonString = asJson("past-meeting-participants.json");

        Collection<String> PII = Arrays.asList(
            "8b29rgg4bb", "jjd93a2337", // user ids
            "bob@example.com",
            "joe@example.com",
            "111111111"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize("GET", new URL("https://api.zoom.us/v2/past_meetings/MEETING_ID/participants"), jsonString);

        assertPseudonymized(sanitized, PII);

        assertRedacted(sanitized, "Joe Surname", "Bob S. Smith");
    }

    @SneakyThrows
    @Test
    void report_user_meetings() {
        String jsonString = asJson("report-user-meetings.json");

        Collection<String> PII = Arrays.asList(
            "jchill@example.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = sanitize("https://api.zoom.us/v2/report/users/123123/meetings", jsonString);

        assertPseudonymized(sanitized, PII);

        assertRedacted(sanitized, "My Meeting", "Jill Chill");
    }

    @SneakyThrows
    @Test
    void report_meeting_details() {
        String jsonString = asJson("report-meeting-details.json");
        Collection<String> PII = Arrays.asList(
            "jchill@example.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = sanitize("https://api.zoom.us/v2/report/meetings/12321314", jsonString);

        assertPseudonymized(sanitized, PII);

        assertRedacted(sanitized, "My Meeting", "Jill Chill");
    }

    @SneakyThrows
    @Test
    void report_meeting_participants() {
        String jsonString = asJson("report-meeting-participants.json");
        Collection<String> PII = Arrays.asList(
            "jchill@example.com",
            "111111111"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = sanitize("https://api.zoom.us/v2/report/meetings/12321314/participants", jsonString);

        assertPseudonymized(sanitized, PII);

        assertRedacted(sanitized, "example name", "example registrant id");
    }



    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://api.zoom.us/v2/users/USER_ID/meetings", "list-user-meetings.json"),
            InvocationExample.of("https://api.zoom.us/v2/users", "list-users.json"),
            InvocationExample.of("https://api.zoom.us/v2/meetings/MEETING_ID", "meeting-details.json"),
            InvocationExample.of("https://api.zoom.us/v2/past_meetings/MEETING_ID", "past-meeting-details.json"),
            InvocationExample.of("https://api.zoom.us/v2/past_meetings/MEETING_ID/instances", "past-meeting-instances.json"),
            InvocationExample.of("https://api.zoom.us/v2/past_meetings/MEETING_ID/participants", "past-meeting-participants.json"),

            InvocationExample.of("https://api.zoom.us/v2/report/users/{userId}/meetings", "report-user-meetings.json"),
            InvocationExample.of("https://api.zoom.us/v2/report/meetings/{meetingId}", "report-meeting-details.json"),
            InvocationExample.of("https://api.zoom.us/v2/report/meetings/{meetingId}/participants", "report-meeting-participants.json")
        );
    }

}
