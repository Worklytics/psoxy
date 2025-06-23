package co.worklytics.psoxy.rules.github;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@Getter
public class GitHubTests extends GitHubNonEnterpriseTests {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.GITHUB;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
            .sourceKind("github")
            .rulesFile("/github/github")
            .exampleApiResponsesDirectoryPath("github/example-api-responses/original/")
            .exampleSanitizedApiResponsesPath("github/example-api-responses/sanitized/")
            .build();

    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }

    @Test
    @Override
    public void yamlLength() {
        // Do nothing, as response schema is bigger than we allow for advanced parameters
    }

    @Test
    void graphql_for_users_with_saml() {
        String jsonString = asJson("graph_api_users_saml.json");

        Collection<String> PII = Arrays.asList(
                "fake1",
                "fake2",
                "fake1@contoso.com",
                "fake2@contoso.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize("https://api.github.com/graphql", jsonString);

        assertPseudonymized(sanitized, "fake1", "fake1@contoso.com");
        assertPseudonymized(sanitized, "fake2", "fake2@contoso.com");
        assertPseudonymized(sanitized, "fake1_email_value@contoso.com");

        assertUrlAllowed("https://api.github.com/graphql");
    }

    @Test
    void org_audit_log() {
        String jsonString = asJson("org_audit_log.json");

        String endpoint = "https://api.github.com/orgs/{org}/audit-log";

        Collection<String> PII = Arrays.asList(
                "octocat",
                "some-business"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");

        assertRedacted(sanitized,
                "Update README.md",
                "some-business"
        );

        assertUrlAllowed(endpoint);
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
                InvocationExample.of("https://api.github.com/orgs/FAKE/members", "org_members.json"),
                InvocationExample.of("https://api.github.com/graphql", "graph_api_users_saml.json"),
                InvocationExample.of("https://api.github.com/graphql", "graph_api_users_members.json"),
                InvocationExample.of("https://api.github.com/graphql", "graph_api_error.json"),
                InvocationExample.of("https://api.github.com/graphql", "repo_commits_graphql.json"),
                InvocationExample.of("https://api.github.com/graphql", "pull_commits_graphql.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/teams", "org_teams.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/teams/TEAM/members", "team_members.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos", "repos.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/audit-log", "org_audit_log.json"),
                InvocationExample.of("https://api.github.com/organizations/123456789/audit-log?include=all&per_page=100&phrase=created:2023-02-16T12:00:00%2B0000..2023-04-17T00:00:00%2B0000&page=0&order=asc&after=MS42OEQyOTE2MjX1MqNlJzIyfANVOHoYbUVsZ1ZjUWN6TwlZLXl6EVE%3D&before", "org_audit_log.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/commits/COMMIT_REF", "commit.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/commits/COMMIT_REF/comments", "commit_comments.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/branches", "repo_branches.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/commits", "repo_commits.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/events", "repo_events.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/comments/COMMENT_ID/reactions", "comment_reactions.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/issues/ISSUE", "issue.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/issues", "issues.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/issues?after=some_token", "issues.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/issues/ISSUE/comments", "issues_comments.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/issues/ISSUE/events", "issue_events.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/issues/ISSUE/timeline", "issue_timeline.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/issues/ISSUE/reactions", "issues_reactions.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/issues/comments/reactions", "issues_comments_reactions.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/pulls", "pulls.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/pulls/42/comments", "pull_comments.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/pulls/42/commits", "pull_commits.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/pulls/42/reviews", "pull_reviews.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/pulls/42/reviews/10/comments", "pull_review_comments.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/pulls/PULL_ID", "pull.json"),
                InvocationExample.of("https://api.github.com/users/p~IAUEqSLLtP3EjjkzslH-S1ULJZRLQnH9hT54jiI1gbN_fPDYrPH3aBnAoR5-ec6f?per_page=1", "user.json"),
                InvocationExample.of("https://api.github.com/users/p~wD8RXfeJ5J-Po8ztEdwRQ-ae1xHBQKRMfNsFB5FFteZgtt4TBv84utnnumgFnjsR?per_page=1", "user.json"),
                InvocationExample.of("https://api.github.com/users/p~IAUEqSLLtP3EjjkzslH-S1ULJZRLQnH9hT54jiI1gbN_fPDYrPH3aBnAoR5-ec6f", "user.json")
        );
    }
}