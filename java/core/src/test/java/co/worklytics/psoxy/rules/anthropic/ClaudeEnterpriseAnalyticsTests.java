package co.worklytics.psoxy.rules.anthropic;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;

import java.util.stream.Stream;

@Getter
public class ClaudeEnterpriseAnalyticsTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.CLAUDE_ENTERPRISE_ANALYTICS;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .defaultScopeId("claude")
        .sourceKind("claude-enterprise-analytics")
        .exampleApiResponsesDirectoryPath("example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized/")
        .sourceFamily("anthropic")
        .checkUncompressedSSMLength(false)
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            // /users — page 1 (no cursor)
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/users?date=2026-06-01", "users.json"),
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/users?date=2026-06-01&limit=100", "users.json"),
            // /users — page 2+ (cursor pagination)
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/users?date=2026-06-01&page=cursor_token_abc", "users.json"),
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/users?date=2026-06-01&page=cursor_token_abc&limit=100", "users.json"),

            // /apps/chat/projects — page 1
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/apps/chat/projects?date=2026-06-01", "apps_chat_projects.json"),
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/apps/chat/projects?date=2026-06-01&limit=50", "apps_chat_projects.json"),
            // /apps/chat/projects — page 2+ (cursor pagination)
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/apps/chat/projects?date=2026-06-01&page=cursor_token_def", "apps_chat_projects.json"),
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/apps/chat/projects?date=2026-06-01&page=cursor_token_def&limit=50", "apps_chat_projects.json"),

            // /user_usage_report — page 1
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/user_usage_report?starting_at=2026-06-01&ending_at=2026-06-08", "user_usage_report.json"),
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/user_usage_report?starting_at=2026-06-01&ending_at=2026-06-08&limit=100", "user_usage_report.json"),
            // /user_usage_report — page 2+ (cursor pagination)
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/user_usage_report?starting_at=2026-06-01&ending_at=2026-06-08&page=cursor_token_ghi", "user_usage_report.json"),
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/user_usage_report?starting_at=2026-06-01&ending_at=2026-06-08&page=cursor_token_ghi&limit=100", "user_usage_report.json"),
            // /user_usage_report — with optional filter params
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/user_usage_report?starting_at=2026-06-01&ending_at=2026-06-08&models[]=claude-opus-4-5&order=desc&order_by=total_tokens", "user_usage_report.json"),

            // /user_cost_report — page 1
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/user_cost_report?starting_at=2026-06-01&ending_at=2026-06-08", "user_cost_report.json"),
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/user_cost_report?starting_at=2026-06-01&ending_at=2026-06-08&limit=100", "user_cost_report.json"),
            // /user_cost_report — page 2+ (cursor pagination; has_more=false in example so next_page is null)
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/user_cost_report?starting_at=2026-06-01&ending_at=2026-06-08&page=cursor_page_2", "user_cost_report.json"),
            // /user_cost_report — with optional filter params
            InvocationExample.of("https://api.anthropic.com/v1/organizations/analytics/user_cost_report?starting_at=2026-06-01&ending_at=2026-06-08&products[]=claude&order=asc&order_by=amount", "user_cost_report.json"),

            // /spend_limits/effective — page 1
            InvocationExample.of("https://api.anthropic.com/v1/organizations/spend_limits/effective?limit=20", "spend_limits_effective.json"),
            // /spend_limits/effective — page 2+ (cursor pagination)
            InvocationExample.of("https://api.anthropic.com/v1/organizations/spend_limits/effective?limit=20&page=cursor_token_jkl", "spend_limits_effective.json"),
            // /spend_limits/effective — always requested with all three periods
            InvocationExample.of("https://api.anthropic.com/v1/organizations/spend_limits/effective?period[]=daily&period[]=weekly&period[]=monthly&limit=20", "spend_limits_effective.json"),
            // /spend_limits/{spend_limit_id} — single-record lookup, not called by the ingestion job but allow-listed
            InvocationExample.of("https://api.anthropic.com/v1/organizations/spend_limits/spl_01XyZaBcDeFgHiJkLmNoPq", "spend_limit_effective_single.json"),

            // /spend_limit_increase_requests — page 1
            InvocationExample.of("https://api.anthropic.com/v1/organizations/spend_limit_increase_requests?status[]=pending&limit=50", "spend_limit_increase_requests.json"),
            // /spend_limit_increase_requests — page 2+ (cursor pagination; no next_page in example so null)
            InvocationExample.of("https://api.anthropic.com/v1/organizations/spend_limit_increase_requests?limit=50&page=cursor_page_2", "spend_limit_increase_requests.json"),
            // /spend_limit_increase_requests/{spend_limit_increase_request_id} — single-record lookup, not called by the ingestion job but allow-listed
            InvocationExample.of("https://api.anthropic.com/v1/organizations/spend_limit_increase_requests/slir_01AbCdEfGhIjKlMnOpQrSt", "spend_limit_increase_request_single.json")
        );
    }
}
