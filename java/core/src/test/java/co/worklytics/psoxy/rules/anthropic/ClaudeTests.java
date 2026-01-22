package co.worklytics.psoxy.rules.anthropic;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class ClaudeTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.CLAUDE_CODE;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("claude")
        .sourceKind("claude-code")
        .exampleApiResponsesDirectoryPath("example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized/")
        .sourceFamily("anthropic")
        .checkUncompressedSSMLength(false)
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://platform.claude.com/v1/organizations/users", "list-users.json"),
            InvocationExample.of("https://platform.claude.com/v1/organizations/users?after_id=something&limit=10", "list-users.json"),
            InvocationExample.of("https://platform.claude.com/v1/organizations/usage_report/claude_code", "claude-code-usage-report.json"),
            InvocationExample.of("https://platform.claude.com/v1/organizations/usage_report/claude_code?starting_at=2026-01-01&page=something&limit=50", "claude-code-usage-report.json")
        );
    }
}
