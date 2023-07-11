package co.worklytics.psoxy.rules.github;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public class GitHubTests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.GITHUB;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/github";

    @Getter
    final String defaultScopeId = "github";

    @Getter
    final String yamlSerializationFilepath = "github/github";

    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }

    @SneakyThrows
    @Test
    @Override
    @Disabled
    public void yamlLength() {
        // Do nothing, as response schema is bigger than we allow for advanced parameters
    }

    @Test
    void orgMembers() {
        String jsonString = asJson(exampleDirectoryPath, "org_members.json");

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
    void graphql_for_users() {
        String jsonString = asJson(exampleDirectoryPath, "graph_api_users.json");

        String endpoint = "https://api.github.com/graphql";

        Collection<String> PII = Arrays.asList(
                "fake1",
                "fake2",
                "fake1@contoso.com",
                "fake2@contoso.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "fake1", "fake1@contoso.com");
        assertPseudonymized(sanitized, "fake2", "fake2@contoso.com");

        assertUrlAllowed(endpoint);
    }

    @Test
    void orgTeams() {
        String jsonString = asJson(exampleDirectoryPath, "org_teams.json");

        String endpoint = "https://api.github.com/orgs/FAKE/teams";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertNotSanitized(sanitized, "justice-league");
        assertNotSanitized(sanitized, "123456");

        assertRedacted(sanitized, "Justice League",
                "A great team."
                );

        assertUrlAllowed(endpoint);
    }

    @Test
    void orgTeamMembers() {
        String jsonString = asJson(exampleDirectoryPath, "team_members.json");

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
        String jsonString = asJson(exampleDirectoryPath, "commit.json");

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
        String jsonString = asJson(exampleDirectoryPath, "repo_commits.json");

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
    void comments_reactions() {
        String jsonString = asJson(exampleDirectoryPath, "comment_reactions.json");

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
        String jsonString = asJson(exampleDirectoryPath, "issue.json");

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
        String jsonString = asJson(exampleDirectoryPath, "issues.json");

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
        String jsonString = asJson(exampleDirectoryPath, "issues_comments.json");

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
        String jsonString = asJson(exampleDirectoryPath, "issue_events.json");

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
        String jsonString = asJson(exampleDirectoryPath, "issue_timeline.json");

        String endpoint = "https://api.github.com/repos/FAKE/REPO/issues/ISSUE/timeline";

        Collection<String> PII = Arrays.asList(
                "9919",
                "octocat",
                "67656570",
                "94867353"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
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
        String jsonString = asJson(exampleDirectoryPath, "issues_comments_reactions.json");

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
        String jsonString = asJson(exampleDirectoryPath, "issues_reactions.json");

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
        String jsonString = asJson(exampleDirectoryPath, "repo_events.json");

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
        String jsonString = asJson(exampleDirectoryPath, "pull_reviews.json");

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
        String jsonString = asJson(exampleDirectoryPath, "repos.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repos";

        Collection<String> PII = Arrays.asList(
                "Worklytics-user",
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
        String jsonString = asJson(exampleDirectoryPath, "pulls.json");

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

        assertNotSanitized(sanitized, "octocatorg");
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
        String jsonString = asJson(exampleDirectoryPath, "pull.json");

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
        String jsonString = asJson(exampleDirectoryPath, "pull_commits.json");

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
        String jsonString = asJson(exampleDirectoryPath, "pull_comments.json");

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
        String jsonString = asJson(exampleDirectoryPath, "pull_review_comments.json");

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
        String jsonString = asJson(exampleDirectoryPath, "org_audit_log.json");

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
                InvocationExample.of("https://api.github.com/graphql", "graph_api_users.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/teams", "org_teams.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/teams/TEAM/members", "team_members.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos", "repos.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/audit-log", "org_audit_log.json"),
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/commits/COMMIT_REF", "commit.json"),
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
                InvocationExample.of("https://api.github.com/repos/FAKE/REPO/pulls/PULL_ID", "pull.json")
        );
    }
}