
package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

//TODO: fix this re-use via inheritance; makes tests brittle; we should inject this rule set into
// the directory tests, or something like that
public class Mail_NoAppIds_NoGroups_Tests extends MailTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_MAIL_NO_APP_IDS_NO_GROUPS;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("microsoft-365")
        .defaultScopeId("azure-ad")
        .sourceKind("outlook-mail")
        .rulesFile("outlook-mail_no-app-ids_no-groups")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized_no-app-ids_no-groups/")
        .build();
}