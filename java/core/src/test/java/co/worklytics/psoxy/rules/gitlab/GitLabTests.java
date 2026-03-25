package co.worklytics.psoxy.rules.gitlab;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

@Getter
public class GitLabTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.GITLAB;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceKind("gitlab")
        .exampleApiResponsesDirectoryPath("example-api-responses/original/")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized/")
        .checkUncompressedSSMLength(false)
        .build();


    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            // Audit Events endpoint
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/audit_events", "audit_log.json"),

            // Groups endpoint - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/groups", "groups.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/groups?page=2&per_page=20", "groups.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/groups?order_by=name&sort=asc&search=foo&top_level_only=true", "groups.json"),

            // Group Members endpoint - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/groups/123/members/all", "group_members.json"),

            // Issues endpoint - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/issues", "issues.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/issues?page=2&per_page=50", "issues.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/issues?state=opened&updated_after=2024-01-01&order_by=updated_at&sort=desc", "issues.json"),

            // Merge Requests endpoint - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/merge_requests", "merge_requests.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/merge_requests?page=2&per_page=50", "merge_requests.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/merge_requests?state=merged&updated_after=2024-01-01&order_by=updated_at&sort=desc", "merge_requests.json"),

            // Single Merge Request
            InvocationExample.of("https://gitlab.example.com/api/v4/merge_requests/1", "merge_request.json"),

            // Projects endpoint - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects", "projects.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects?page=2&per_page=20", "projects.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects?archived=false&visibility=public&order_by=name&sort=asc", "projects.json"),

            // Project Issue Notes - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/issues/42/notes", "issue_notes.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/issues/42/notes?page=2&per_page=20", "issue_notes.json"),

            // Project Issue Resource State Events - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/issues/42/resource_state_events", "issue_events.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/issues/42/resource_state_events?page=2&per_page=20", "issue_events.json"),

            // Project Merge Request Commits - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/merge_requests/1/commits", "merge_request_commits.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/merge_requests/1/commits?page=2&per_page=20", "merge_request_commits.json"),

            // Project Merge Request Notes - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/merge_requests/1/notes", "merge_request_notes.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/merge_requests/1/notes?page=2&per_page=20", "merge_request_notes.json"),

            // Project Merge Request Resource State Events - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/merge_requests/1/resource_state_events", "merge_request_events.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/merge_requests/1/resource_state_events?page=2&per_page=20", "merge_request_events.json"),

            // Project Repository Branches - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/repository/branches", "branches.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/repository/branches?page=2&per_page=20", "branches.json"),

            // Project Repository Commits - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/repository/commits", "repo_commits.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/repository/commits?page=2&per_page=50", "repo_commits.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/repository/commits?ref_name=main&since=2024-01-01&until=2024-12-31&with_stats=true", "repo_commits.json"),

            // Single Repository Commit
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/repository/commits/abc123def", "commit.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/repository/commits/abc123def?stats=true", "commit.json"),

            // Repository Commit Discussions - initial and paginated
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/repository/commits/abc123def/discussions", "commit_discussion.json"),
            InvocationExample.of("https://gitlab.example.com/api/v4/projects/1/repository/commits/abc123def/discussions?page=2&per_page=20", "commit_discussion.json")
        );
    }
}

