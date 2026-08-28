package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.Rules2;
import jdk.jfr.Description;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

@Getter
public class CopilotTests extends JavaRulesTestBaseCase {

    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.MS_COPILOT;

    @Override
    public RulesTestSpec getRulesTestSpec() {
        return RulesTestSpec.builder()
                .sourceFamily("microsoft-365")
                .sourceKind("msft-copilot")
                .checkUncompressedSSMLength(false)
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"beta"})
    @Description("Test endpoint:" + PrebuiltSanitizerRules.MS_COPILOT_INTERACTIONS_PATH)
    public void getAllEnterpriseInteractions(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/copilot/users/some-user/interactionHistory/getAllEnterpriseInteractions";
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
        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        String apiVersion = "beta";
        String baseEndpoint = "https://graph.microsoft.com/" + apiVersion;

        return Stream.of(
                InvocationExample.of(baseEndpoint + "/copilot/users/8b081ef6-4792-4def-b2c9-c363a1bf41d5/interactionHistory/getAllEnterpriseInteractions",
                    "response_" + apiVersion + ".json"),
            InvocationExample.of(baseEndpoint + "/copilot/users/8b081ef6-4792-4def-b2c9-c363a1bf41d5/interactionHistory/getAllEnterpriseInteractions",
                "response_with_team_meeting_" + apiVersion + ".json"),
            // interactionHistory - with all allowed query params
            InvocationExample.of(baseEndpoint + "/copilot/users/8b081ef6-4792-4def-b2c9-c363a1bf41d5/interactionHistory/getAllEnterpriseInteractions?$filter=createdDateTime%20ge%202020-01-01T00:00:00Z",
                "response_" + apiVersion + ".json"),
            // interactionHistory - real shape sent by CopilotInteractionFetchStrategy ($top + compound gt-and-lt date-range $filter together)
            InvocationExample.of(baseEndpoint + "/copilot/users/8b081ef6-4792-4def-b2c9-c363a1bf41d5/interactionHistory/getAllEnterpriseInteractions?$top=100&$filter=createdDateTime%20gt%202020-01-01T00:00:00Z%20and%20createdDateTime%20lt%202020-06-01T00:00:00Z",
                "response_" + apiVersion + ".json"),
            // interactionHistory pagination - real shape of @odata.nextLink from response_beta.json's own
            // captured response ($filter+$skiptoken together, never tested as this combo before)
            InvocationExample.of(baseEndpoint + "/copilot/users/8b081ef6-4792-4def-b2c9-c363a1bf41d5/interactionHistory/getAllEnterpriseInteractions?$filter=appClass+eq+%27IPM.SkypeTeams.Message.Copilot.BizChat%27&$skiptoken=U2tpcFZhbHVlPTAjTWFpbGJveEZvbGRlcj1NYWlsRm9sZGVycy9UZWFtQ2hhdA%3d%3d",
                "response_" + apiVersion + ".json"),
            // interactionHistory pagination - real shape of @odata.nextLink from response_with_team_meeting_beta.json's
            // own captured response ($skiptoken alone, no $filter)
            InvocationExample.of(baseEndpoint + "/copilot/users/8b081ef6-4792-4def-b2c9-c363a1bf41d5/interactionHistory/getAllEnterpriseInteractions?$skiptoken=U2tpcFZhbHVlPTEwI01haWxib3hGb2xkZXI9TWFpbEZvbGRlcnMvVGVhbXNNZXNzYWdlc0RhdGE%3d",
                "response_with_team_meeting_" + apiVersion + ".json"),
            // /v1.0/users/{id} - with all allowed query params
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/8b081ef6-4792-4def-b2c9-c363a1bf41d5?$select=id,mail", "user.json"),
            // /v1.0/users (collection) - with all allowed query params
            InvocationExample.of("https://graph.microsoft.com/v1.0/users?$top=50&$select=id,mail&$skiptoken=abcXYZ123&$orderBy=id&$count=true&$filter=startswith(userPrincipalName,'a')", "users.json")
        );
    }
}
