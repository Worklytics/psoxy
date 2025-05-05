package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.Rules2;
import jdk.jfr.Description;
import lombok.Getter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Getter
public class CopilotNoUserIdsTests extends JavaRulesTestBaseCase {

    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.MS_COPILOT_NO_USER_ID;

    private final static String SAMPLE_USER_ID = "p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug";

    @Override
    public RulesTestSpec getRulesTestSpec() {
        return RulesTestSpec.builder()
                .sourceFamily("microsoft-365")
                .sourceKind("msft-copilot")
            .rulesFile("msft-copilot_no-userIds")
            .exampleSanitizedApiResponsesPath("example-api-responses/sanitized_no-userIds/")
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"beta"})
    @Description("Test endpoint:" + PrebuiltSanitizerRules.MS_COPILOT_INTERACTIONS_PATH)
    public void getAllEnterpriseInteractions(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/copilot/users/" + SAMPLE_USER_ID + "/interactionHistory/getAllEnterpriseInteractions";
        String jsonResponse = asJson("response_" + apiVersion + ".json");
        assertNotSanitized(jsonResponse,
                "fb8d773d-7ef8-4ec0-a117-179f88add510",
                "4db02e4b-d144-400e-b194-53253a34c5be",
                "Meeting"
        );

        String sanitized = sanitize(endpoint, jsonResponse);

        assertRedacted(sanitized,
                "It looks like there were no important emails from last week",
                "test meeting2 - export api",

                "What should be on my radar from emails last week?"
        );

        assertPseudonymized(sanitized, "4db02e4b-d144-400e-b194-53253a34c5be");

        // Bot IDs are not sanitized because they are not considered sensitive user data 
        // and are required for system functionality, such as identifying automated processes.
        assertNotSanitized(sanitized, "fb8d773d-7ef8-4ec0-a117-179f88add510");

        assertReversibleUrlTokenized(sanitized, List.of("19:YzBP1kUdkNjFtJnketPYT8kQdQ3A08Y51rDTxE_ENIk1@thread.v2")
        );

        // Cannot be included in assertReverseUrlTokenized because it is present as part of user id and in the URL
        assertTrue(sanitized.contains("p~sMxhtJVS27r5MC4y2Z_akFQRDkAHruk6R5wDIq6powZ1iAsVwy2YDKEPVu2q6nfypG3bROgcL5AJcqe_h3YVyT0ZO6AlXOS3vSV6HXg1BeQ"));

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        String apiVersion = "beta";
        String baseEndpoint = "https://graph.microsoft.com/" + apiVersion;

        return Stream.of(
                InvocationExample.of(baseEndpoint + "/copilot/users/" + SAMPLE_USER_ID + "/interactionHistory/getAllEnterpriseInteractions",
                    "response_" + apiVersion + ".json"),
            InvocationExample.of(baseEndpoint + "/copilot/users/" + SAMPLE_USER_ID + "/interactionHistory/getAllEnterpriseInteractions",
                "response_with_team_meeting_" + apiVersion + ".json")
        );
    }
}
