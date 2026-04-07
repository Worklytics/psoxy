package co.worklytics.psoxy.rules.cursor;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class CursorTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.CURSOR;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("cursor")
        .sourceKind("cursor")
        .exampleApiResponsesDirectoryPath("example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized/")
        .checkUncompressedSSMLength(false)
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            // Daily Usage Data endpoint (POST)
            InvocationExample.of("https://api.cursor.com/teams/daily-usage-data", "daily-usage-data.json", "POST"),

            // Filtered Usage Events endpoint (POST)
            InvocationExample.of("https://api.cursor.com/teams/filtered-usage-events", "filtered-usage-events.json", "POST"),

            // Team Members endpoint (GET)
            InvocationExample.of("https://api.cursor.com/teams/members", "team-members.json")
        );
    }
}

