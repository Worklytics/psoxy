package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;

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
}
