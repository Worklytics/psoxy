package co.worklytics.psoxy.rules.zoom;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.Rules1;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class ZoomRulesTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules1 rulesUnderTest = PrebuiltSanitizerRules.ZOOM;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/zoom";

    @Getter
    final String defaultScopeId = "zoom";

    @Getter
    final String yamlSerializationFilepath = "zoom/zoom";

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
    })
    @ParameterizedTest
    void allowedEndpointRegex_allowed(String url) {
        assertTrue(sanitizer.isAllowed(new URL(url)), url + " should be allowed");
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
        assertFalse(sanitizer.isAllowed(new URL(url)), url + " should be blocked");
    }

    @SneakyThrows
    @Test
    void list_users() {
        String jsonString = asJson("list-users.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "example@example.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("https://api.zoom.us/v2/users"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Taylor", "Kim", "https://example.com/photo.jpg");
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
            sanitizer.sanitize(new URL("https://api.zoom.us/v2/users/ANY_USER_ID/meetings"), jsonString);

        assertPseudonymized(sanitized, PII);
        // topics & join_urls gone
        assertRedacted(sanitized, "https://zoom.us", "Zoom Meeting", "TestMeeting", "My Meeting", "MyTestPollMeeting");
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
            sanitizer.sanitize(new URL("https://api.zoom.us/v2/meetings/MEETING_ID?show_previous_occurrences=false"), jsonString);

        assertPseudonymized(sanitized, PII);

        assertRedacted(sanitized, "example@example.com", "API overview", "My API Test");
    }

    @SneakyThrows
    @Test
    void list_past_meeting_instances() {
        String jsonString = asJson("past-meeting-instances.json");

        String sanitized =
            sanitizer.sanitize(new URL("https://api.zoom.us/v2/past_meetings/MEETING_ID/instances"), jsonString);

        // no rules here
        assertEquals(jsonString, sanitized);
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
            sanitizer.sanitize(new URL("https://api.zoom.us/v2/past_meetings/MEETING_ID"), jsonString);

        assertPseudonymized(sanitized, PII);

        assertRedacted(sanitized, "Joe Surname", "My Meeting");
    }

    @SneakyThrows
    @Test
    void past_meeting_participants() {
        String jsonString = asJson("past-meeting-participants.json");

        Collection<String> PII = Arrays.asList(
            "8b29rgg4bb", "jjd93a2337", // user ids
            "bob@example.com",
            "joe@example.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("https://api.zoom.us/v2/past_meetings/MEETING_ID/participants"), jsonString);

        assertPseudonymized(sanitized, PII);

        assertRedacted(sanitized, "Joe Surname", "Bob S. Smith");
    }

}
