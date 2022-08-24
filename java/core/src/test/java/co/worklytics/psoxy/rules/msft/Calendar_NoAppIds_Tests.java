package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

//TODO: fix this re-use via inheritance; makes tests brittle; we should inject this rule set into
// the directory tests, or something like that
public class Calendar_NoAppIds_Tests extends CalendarTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.OUTLOOK_CALENDAR_NO_APP_IDS;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/microsoft-365/outlook-cal";

    @Getter
    final String defaultScopeId = "azure-ad";

    @BeforeEach
    public void setTestSpec() {
        this.setTestSpec(RulesTestSpec.builder()
            .yamlSerializationFilePath("microsoft-365/outlook-cal_no-app-ids")
            .sanitizedExamplesDirectoryPath("api-response-examples/microsoft-365/outlook-cal/no-app-ids")
            .build());
    }
}
