package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

//TODO: fix this re-use via inheritance; makes tests brittle; we should inject this rule set into
// the directory tests, or something like that
public class Calendar_NoAppIds_NoGroups_Tests extends CalendarTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_CALENDAR_NO_APP_IDS_NO_GROUPS;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/microsoft-365/outlook-cal";

    @Getter
    final String defaultScopeId = "azure-ad";

    @BeforeEach
    public void setTestSpec() {
        this.setTestSpec(RulesTestSpec.builder()
            .yamlSerializationFilePath("microsoft-365/outlook-cal_no-app-ids_no-groups")
            .sanitizedExamplesDirectoryPath("api-response-examples/microsoft-365/outlook-cal/no-app-ids_no-groups")
            .build());
    }

    @Override
    @Test
    void group() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315";
        assertUrlWithQueryParamsBlocked(endpoint);
    }

    @Override
    @Test
    void groups() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups";
        assertUrlWithQueryParamsBlocked(endpoint);
    }

    @Test
    void groupMembers() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315/members?$count=true";
        assertUrlWithQueryParamsBlocked(endpoint);
    }
}
