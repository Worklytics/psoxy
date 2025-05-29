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
public class GitHubTests extends JavaRulesTestBaseCase {

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
    void orgMembers() {
        String jsonString = asJson("org_members.json");

        String endpoint = "https://api.github.com/orgs/FAKE/members";

        Collection<String> PII = Arrays.asList(
            "octocat",
            "123456"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "https://api.github.com/users/octocat",
            "https://api.github.com/users/octocat/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
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
    void graphql_for_users_with_members() {
        String jsonString = asJson("graph_api_users_members.json");

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

        assertUrlAllowed("https://api.github.com/graphql");
    }

    @Test
    void user() {
        String jsonString = asJson("user.json");

        String endpoint = "https://api.github.com/users/p~IAUEqSLLtP3EjjkzslH-S1ULJZRLQnH9hT54jiI1gbN_fPDYrPH3aBnAoR5-ec6f";

        Collection<String> PII = Arrays.asList(
            "monalisa octocat",
            "octocat",
            "monatheoctocat"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "octocat@github.com");

        assertUrlAllowed(endpoint);
    }

    @Test
    void orgTeams() {
        String jsonString = asJson("org_teams.json");

        String endpoint = "https://api.github.com/orgs/FAKE/teams";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertNotSanitized(sanitized, "123456");

        assertRedacted(sanitized, "Justice League",
            "A great team.",
            "justice-league"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void orgTeamMembers() {
        String jsonString = asJson("team_members.json");

        String endpoint = "https://api.github.com/orgs/FAKE/teams/TEAM/members";

        Collection<String> PII = Arrays.asList(
            "some-user",
            "12345678"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "some-user");
        assertPseudonymized(sanitized, "12345678");
        assertRedacted(sanitized,
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void repoCommit() {
        String jsonString = asJson("commit.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/commits/COMMIT_REF";

        Collection<String> PII = Arrays.asList(
            "Monalisa Octocat",
            "octocat"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "Monalisa Octocat",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void repositoryCommits() {
        String jsonString = asJson("repo_commits.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/commits";

        Collection<String> PII = Arrays.asList(
            "Monalisa Octocat",
            "octocat"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "Monalisa Octocat",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void commit_comments() {
        String jsonString = asJson("commit_comments.json");

        String endpoint = "https://api.github.com/repos/{owner}/{repo}/commits/{commit_sha}/comments";

        Collection<String> PII = Arrays.asList(
            "octocat"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertUrlAllowed(endpoint);
    }

    @Test
    void comments_reactions() {
        String jsonString = asJson("comment_reactions.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/comments/COMMENT/reactions";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "98765432");
        assertRedacted(sanitized,
            "Monalisa Octocat",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void issue() {
        String jsonString = asJson("issue.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/issues/ISSUE_NUMBER";

        Collection<String> PII = List.of(
            "octocat"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "I'm having a problem with this.",
            "Found a bug",
            "Tracking milestone for version 1.0",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void issues() {
        String jsonString = asJson("issues.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/issues?page=5";

        Collection<String> PII = Collections.singletonList("octocat");

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "I'm having a problem with this.",
            "Found a bug",
            "Tracking milestone for version 1.0",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void issue_comments() {
        String jsonString = asJson("issues_comments.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/issues/ISSUE/comments";

        Collection<String> PII = Collections.singletonList("octocat");

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "Me too",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void issue_events() {
        String jsonString = asJson("issue_events.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/issues/ISSUE_ID/events";

        Collection<String> PII = Collections.singletonList("octocat");

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void issue_timeline() {
        String jsonString = asJson("issue_timeline.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/issues/ISSUE/timeline";

        Collection<String> PII = Arrays.asList(
            "9919",
            "67656570",
            "94867353",
            "octocat",
            "authorUser@some-domain.com",
            "noreply@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "authorUser@some-domain.com");
        assertPseudonymized(sanitized, "noreply@github.com");
        assertPseudonymized(sanitized, "9919");
        assertPseudonymized(sanitized, "67656570");
        assertPseudonymized(sanitized, "94867353");
        assertRedacted(sanitized,
            "Shipped to the cloud",
            "Secret scanning",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void issue_comment_reactions() {
        String jsonString = asJson("issues_comments_reactions.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/issues/comments/COMMENT/reactions";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "98765432");
        assertRedacted(sanitized,
            "Monalisa Octocat",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void issue_reactions() {
        String jsonString = asJson("issues_reactions.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/issues/ISSUE/reactions";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "98765432");
        assertRedacted(sanitized,
            "Monalisa Octocat",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void repo_events() {
        String jsonString = asJson("repo_events.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/events";

        Collection<String> PII = Arrays.asList(
            "Monalisa Octocat",
            "octocat",
            "octocat@github.com",
            "583231"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "583231");
        assertPseudonymized(sanitized, "octocat@github.com");
        assertRedacted(sanitized,
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void pull_reviews() {
        String jsonString = asJson("pull_reviews.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/pulls/PULL_NUMBER/reviews";

        Collection<String> PII = Arrays.asList(
            "123456",
            "octocat"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "Here is the body for the review.",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void repositories() {
        String jsonString = asJson("repos.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repos";

        Collection<String> PII = Arrays.asList(
            "Worklytics-org",
            "23456789"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        //assertPseudonymized(sanitized, "Worklytics-user");
        //assertPseudonymized(sanitized, "23456789");
        assertRedacted(sanitized,
            "serverless, pseudonymizing proxy between Worklytics and your SaaS workplace data sources' REST APIs",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void pulls() {
        String jsonString = asJson("pulls.json");

        String endpoint = "https://api.github.com/repos/{owner}/{repo}/pulls";

        Collection<String> PII = Arrays.asList(
            "octocat",
            "123456",
            "hubot",
            "123457",
            "other_user",
            "123458"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocatorg");
        assertPseudonymized(sanitized, "hubot");
        assertPseudonymized(sanitized, "123457");
        assertPseudonymized(sanitized, "123458");

        assertRedacted(sanitized,
            "Amazing new feature",
            "Please pull these awesome changes in!",
            "Something isn't working",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void pull() {
        String jsonString = asJson("pull.json");

        String endpoint = "https://api.github.com/repos/{owner}/{repo}/pulls/PULL_NUMBER";

        Collection<String> PII = Arrays.asList(
            "octocat",
            "123456",
            "hubot",
            "123457",
            "other_user",
            "123458"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "123456");
        assertPseudonymized(sanitized, "hubot");
        assertPseudonymized(sanitized, "123457");
        assertPseudonymized(sanitized, "123458");

        assertRedacted(sanitized,
            "Amazing new feature",
            "Please pull these awesome changes in!",
            "Something isn't working",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void pullCommits() {
        String jsonString = asJson("pull_commits.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/pulls/42/commits";

        Collection<String> PII = Arrays.asList(
            "Monalisa Octocat",
            "octocat"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "Monalisa Octocat",
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void pullComments() {
        String jsonString = asJson("pull_comments.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/pulls/42/comments";

        Collection<String> PII = List.of(
            "some-user"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "some-user");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void pullReviewComments() {
        String jsonString = asJson("pull_review_comments.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/pulls/42/reviews/10/comments";

        Collection<String> PII = List.of(
            "some-user"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "some-user");
        assertPseudonymized(sanitized, "123456");
        assertRedacted(sanitized,
            "https://api.github.com/users/some-user",
            "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
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
