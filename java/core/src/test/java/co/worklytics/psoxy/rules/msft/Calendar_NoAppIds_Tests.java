package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import jdk.jfr.Description;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

//TODO: fix this re-use via inheritance; makes tests brittle; we should inject this rule set into
// the directory tests, or something like that
public class Calendar_NoAppIds_Tests extends CalendarTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_CALENDAR_NO_APP_IDS;


    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("microsoft-365")
        .defaultScopeId("azure-ad")
        .sourceKind("outlook-cal")
        .rulesFile("outlook-cal_no-app-ids")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized_no-app-ids/")
        .build();

    @ParameterizedTest
    @ValueSource(strings = {"v1.0"})
    @Description("Test endpoint: " + PrebuiltSanitizerRules.ONLINE_MEETINGS_PATH_TEMPLATES)
    @Override
    public void users_onlineMeetings(String apiVersion) {
        String userId = "dc17674c-81d9-4adb-bfb2-8f6a442e4622";
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/users/" + userId + "/onlineMeetings";
        String jsonResponse = asJson("Users_onlineMeetings_"+ apiVersion + ".json");

        String sanitized = sanitize(endpoint, jsonResponse);
        assertPseudonymized(sanitized, "112f7296-5ca-bae8-6a692b15d4b8", "5810cedeb-b2c1-e9bd5d53ec96");
        assertRedacted(sanitized,
                "@odata.type",
                "#microsoft.graph.onlineMeeting",
                "#microsoft.graph.chatInfo",
                "#microsoft.graph.meetingParticipants",
                "#microsoft.graph.identitySet",
                "#microsoft.graph.identity"
        );
        assertUrlWithSubResourcesBlocked(endpoint);
    }
}