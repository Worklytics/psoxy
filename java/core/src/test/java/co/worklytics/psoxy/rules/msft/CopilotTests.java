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
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"v1.0", "beta"})
    @Description("Test Copilot interaction export on Graph v1.0 and beta paths")
    public void getAllEnterpriseInteractions(String apiVersion) {
        String endpoint = "https://graph.microsoft.com/" + apiVersion + "/copilot/users/some-user/interactionHistory/getAllEnterpriseInteractions";
        String jsonResponse = asJson("response_beta.json");
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
        String userPath =
            "/copilot/users/8b081ef6-4792-4def-b2c9-c363a1bf41d5/interactionHistory/getAllEnterpriseInteractions";

        return Stream.of(
            InvocationExample.of("https://graph.microsoft.com/v1.0" + userPath, "response_beta.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0" + userPath, "response_with_team_meeting_beta.json"),
            InvocationExample.of("https://graph.microsoft.com/beta" + userPath, "response_beta.json"),
            InvocationExample.of("https://graph.microsoft.com/beta" + userPath, "response_with_team_meeting_beta.json")
        );
    }
}
