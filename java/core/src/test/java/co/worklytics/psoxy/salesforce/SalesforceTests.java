package co.worklytics.psoxy.salesforce;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.salesforce.PrebuiltSanitizerRules;
import lombok.Getter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Stream;

public class SalesforceTests extends JavaRulesTestBaseCase {

    @Getter
    final RuleSet rulesUnderTest = PrebuiltSanitizerRules.SALESFORCE;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/salesforce";

    @Getter
    final String defaultScopeId = "salesforce";

    @Getter
    final String yamlSerializationFilepath = "salesforce/salesforce";

    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return new ArrayList<InvocationExample>().stream();
    }
}