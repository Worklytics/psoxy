package co.worklytics.psoxy.salesforce;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.salesforce.PrebuiltSanitizerRules;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;

public class SalesforceNoUserIdsTests extends SalesforceTests {

    @Getter
    final RuleSet rulesUnderTest = PrebuiltSanitizerRules.SALESFORCE;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/salesforce";

    @Getter
    final String defaultScopeId = "salesforce";

    @Getter
    final String yamlSerializationFilepath = "salesforce/salesforce";

    @BeforeEach
    public void setTestSpec() {
        this.setTestSpec(RulesTestSpec.builder()
                .yamlSerializationFilePath("salesforce/salesforce_no_user_id")
                .sanitizedExamplesDirectoryPath("api-response-examples/salesforce/sanitized-no-user-ids")
                .build());
    }
}