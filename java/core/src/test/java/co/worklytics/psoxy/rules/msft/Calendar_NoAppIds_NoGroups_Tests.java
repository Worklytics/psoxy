package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

//TODO: fix this re-use via inheritance; makes tests brittle; we should inject this rule set into
// the directory tests, or something like that
public class Calendar_NoAppIds_NoGroups_Tests extends Calendar_NoAppIds_Tests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_CALENDAR_NO_APP_IDS_NO_GROUPS;

    @Getter
    RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("microsoft-365")
        .defaultScopeId("azure-ad")
        .sourceKind("outlook-cal")
        .rulesFile("outlook-cal_no-app-ids_no-groups")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized_no-app-ids_no-groups/")
        .checkUncompressedSSMLength(false)
        .build();

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

    @Override
    public Stream<InvocationExample> getExamples() {
        // groups/{id}/members has no registered endpoint at all in this "no-groups" variant
        // (unlike groups/{id}, which is registered but blocks query params), so the parent's
        // combined-params example for it can't be reused here.
        return super.getExamples().filter(example -> !example.getPlainExampleFile().equals("group-members.json"));
    }
}