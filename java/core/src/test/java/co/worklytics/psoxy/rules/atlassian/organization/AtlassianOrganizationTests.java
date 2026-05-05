package co.worklytics.psoxy.rules.atlassian.organization;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

@Getter
public class AtlassianOrganizationTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.ORGANIZATION;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("atlassian")
        .sourceKind("organization")
        .rulesFile("organization")
        .build();

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            // Audit Events - regular
            InvocationExample.of(
                "https://api.atlassian.com/admin/v1/orgs/org-456/events",
                "get-audit-events-response.json"
            ),
            // Audit Events - paginated
            InvocationExample.of(
                "https://api.atlassian.com/admin/v1/orgs/org-456/events?cursor=abc123",
                "get-audit-events-response.json"
            ),
            // Audit Events Stream - regular
            InvocationExample.of(
                "https://api.atlassian.com/admin/v1/orgs/org-456/events-stream?limit=100",
                "get-audit-events-stream-response.json"
            ),
            // Audit Events Stream - paginated
            InvocationExample.of(
                "https://api.atlassian.com/admin/v1/orgs/org-456/events-stream?cursor=stream_cursor_123",
                "get-audit-events-stream-response.json"
            ),
            // Directory Users - regular
            InvocationExample.of(
                "https://api.atlassian.com/admin/v2/orgs/org-456/directories/dir-789/users",
                "get-users-response.json"
            ),
            // Directory Users - paginated
            InvocationExample.of(
                "https://api.atlassian.com/admin/v2/orgs/org-456/directories/dir-789/users?cursor=xyz789",
                "get-users-response.json"
            )
        );
    }
}

