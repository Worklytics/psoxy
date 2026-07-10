package co.worklytics.psoxy.rules.chatgpt;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class CodexEnterpriseAnalyticsTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.CODEX_ENTERPRISE_ANALYTICS;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("codex-enterprise-analytics")
        .sourceKind("codex-enterprise-analytics")
        .exampleApiResponsesDirectoryPath("example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized/")
        .checkUncompressedSSMLength(false)
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            // /usage — page 1 (no cursor)
            InvocationExample.of("https://api.chatgpt.com/v1/analytics/codex/workspaces/ws_abc123/usage?start_time=1780272000&end_time=1780358400", "usage.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/analytics/codex/workspaces/ws_abc123/usage?start_time=1780272000&end_time=1780358400&limit=100", "usage.json"),
            // /usage — page 2+ (cursor pagination)
            InvocationExample.of("https://api.chatgpt.com/v1/analytics/codex/workspaces/ws_abc123/usage?start_time=1780272000&end_time=1780358400&page=cursor_token_abc", "usage.json"),
            InvocationExample.of("https://api.chatgpt.com/v1/analytics/codex/workspaces/ws_abc123/usage?start_time=1780272000&end_time=1780358400&page=cursor_token_abc&limit=100", "usage.json")
        );
    }
}
