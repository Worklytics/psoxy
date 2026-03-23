package co.worklytics.psoxy.rules.gong;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class GongMetricsTest extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.GONG_METRICS;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("gong")
        .sourceKind("gong-metrics")
        .exampleApiResponsesDirectoryPath("example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized/")
        .checkUncompressedSSMLength(false)
        .sourceFamily("gong")
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://api.gong.io/v2/stats/activity/aggregate",
                "activity_aggregate.json",
                "POST"),
            InvocationExample.of("https://api.gong.io/v2/users", "users.json",
                "GET")
        );
    }
}
