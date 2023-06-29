package co.worklytics.psoxy.rules.github;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
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


    @Test
    void orgMembers() {
        String jsonString = asJson(exampleDirectoryPath, "org_members.json");

        String endpoint = "https://api.github.com/orgs/FAKE/members";

        Collection<String> PII = Arrays.asList(
                "octocat",
                "1"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
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
                "some_user_id",
                "1"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");

        assertUrlAllowed(endpoint);
    }

    @Test
    void orgTeams() {
        String jsonString = asJson(exampleDirectoryPath, "org_teams.json");

        String endpoint = "https://api.github.com/orgs/FAKE/teams";

        Collection<String> PII = Arrays.asList(
                "octocat",
                "1"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertNotSanitized(sanitized, "justice-league");
        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");

        assertUrlAllowed(endpoint);
    }

    @Test
    void orgTeamMembers() {
        String jsonString = asJson(exampleDirectoryPath, "org_teams.json");

        String endpoint = "https://api.github.com/orgs/FAKE/teams/TEAM/members";

        Collection<String> PII = Arrays.asList(
                "some-user",
                "12345678"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "12345678");
        assertRedacted(sanitized,
                "https://api.github.com/users/some-user",
                "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void repoCommits() {
        String jsonString = asJson(exampleDirectoryPath, "repo_commits.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/commits";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
        assertRedacted(sanitized,
                "Monalisa Octocat",
                "https://api.github.com/users/some-user",
                "https://api.github.com/users/some-user/events{/privacy}"
        );

        assertUrlAllowed(endpoint);
    }

    @Test
    void comments_reactions() {
        String jsonString = asJson(exampleDirectoryPath, "comments_reactions.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/comments/COMMENT/reactions";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/issues/ISSUE";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/issues?page=5";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/issues/ISSUE/comments";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void issue_events() {
        String jsonString = asJson(exampleDirectoryPath, "issue_events.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/issues/events";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void issue_timeline() {
        String jsonString = asJson(exampleDirectoryPath, "issue_timeline.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/issues/ISSUE/timeline";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void issue_comment_reactions() {
        String jsonString = asJson(exampleDirectoryPath, "issues_comments_reactions.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/issues/ISSUE/comments/COMMENT/reactions";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void issue_reactions() {
        String jsonString = asJson(exampleDirectoryPath, "issues_reactions.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/issues/ISSUE/reactions/";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void repo_events() {
        String jsonString = asJson(exampleDirectoryPath, "repo_events.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/events";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void pull_reviews() {
        String jsonString = asJson(exampleDirectoryPath, "pull_reviews.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/events";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void pull_review_comments() {
        String jsonString = asJson(exampleDirectoryPath, "pull_review_comments.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO/events";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void repositories() {
        String jsonString = asJson(exampleDirectoryPath, "repos.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void repositories_commit_comments() {
        String jsonString = asJson(exampleDirectoryPath, "repo_comments.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void releases() {
        String jsonString = asJson(exampleDirectoryPath, "releases.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
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
    void deployments() {
        String jsonString = asJson(exampleDirectoryPath, "deployments.json");

        String endpoint = "https://api.github.com/orgs/FAKE/repo/REPO";

        Collection<String> PII = Arrays.asList(
                "Monalisa Octocat",
                "octocat",
                "support@github.com"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "1");
        assertPseudonymized(sanitized, "support@github.com");
        assertRedacted(sanitized,
                "I'm having a problem with this.",
                "Found a bug",
                "Tracking milestone for version 1.0",
                "https://api.github.com/users/some-user",
                "https://api.github.com/users/some-user/events{/privacy}"
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
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/comments", "repo_comments.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/commits", "repo_commits.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/events", "repo_events.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/comments/COMMENT_ID/reactions", "comment_reactions.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/issues/ISSUE", "issue.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/issues", "issues.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/issues/ISSUE/comments", "issue_comments.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/issues/ISSUE/events", "issue_events.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/issues/ISSUE/timeline", "issue_timeline.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/issues/ISSUE/reactions", "issues_reactions.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/issues/ISSUE/comments/COMMENT_ID/reactions", "issues_comments_reactions.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/pulls", "pulls_reviews.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/pulls/PR_ID/comments", "pulls_comments.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/repos/REPO/pulls/PR_ID/comments/COMMENT_ID/reactions", "pulls_reviews_comments_reactions.json"),
                InvocationExample.of("https://api.dropboxapi.com/2/team/members/list/continue_v2", "member_ist_continue.json")
        );
    }
}