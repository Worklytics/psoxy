package co.worklytics.psoxy.rules.glean;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.glean.PrebuiltSanitizerRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class GleanTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.GLEAN;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("glean")
        .sourceKind("glean")
        .exampleApiResponsesDirectoryPath("example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized/")
        .checkUncompressedSSMLength(false)
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://api.glean.com/rest/api/v1/getinsights", "insights-response.json", "POST"),
            InvocationExample.of("https://api.glean.com/rest/api/v1/listentities", "list-entities-response.json", "POST")
        );
    }
}
