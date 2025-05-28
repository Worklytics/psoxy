package co.worklytics.psoxy.rules.github;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
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
public class GitHubCopilotTests extends JavaRulesTestBaseCase {

    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.GITHUB_COPILOT;

    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
            .sourceKind("github")
            .rulesFile("github-copilot")
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
    void org_copilot_seats() {
        String jsonString = asJson("org_copilot_seats.json");

        String endpoint = "https://api.github.com/orgs/FAKE/copilot/billing/seats";

        Collection<String> PII = List.of(
                "octocat"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "octocat");
        assertPseudonymized(sanitized, "octokitten");
        assertRedacted(sanitized,
                "https://api.github.com/users/octocat",
                "https://github.com/octokitten"
        );

        assertUrlAllowed(endpoint);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "?phrase=action:copilot&include=web",
        "?phrase=action:copilot+created:2023-02-16T12:00:00%2B0000..2023-04-17T00:00:00%2B0000&include=web&per_page=100",
        "?phrase=action:copilot+created:2023-02-16T12:00:00%2B0000..2023-04-17T00:00:00%2B0000&include=web&per_page=100&page=0&order=asc&after=MS42OEQyOTE2MjX1MqNlJzIyfANVOHoYbUVsZ1ZjUWN6TwlZLXl6EVE%3D&before",
        "?include=web&phrase=action:copilot",
        "?phrase=action:copilot&include=web",
    })
    void org_audit_log(String query) {
        String jsonString = asJson("org_audit_log.json");

        String endpoint = "https://api.github.com/orgs/{org}/audit-log" + query;

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

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "?include=git",
        "?phrase=action:something",
        "?phrase=action:copilot&include=git",
        "?phrase=action:copilot+action:something&include=git",
        "?phrase=action:copilot+created:2023-02-16T12:00:00%2B0000..2023-04-17T00:00:00%2B0000&page=0&order=asc&after=MS42OEQyOTE2MjX1MqNlJzIyfANVOHoYbUVsZ1ZjUWN6TwlZLXl6EVE%3D&before",
    })
    void org_audit_log_should_block_no_copilot_stuff(String query) {
        String endpoint = "https://api.github.com/orgs/{org}/audit-log" + query;

       assertUrlBlocked(endpoint);
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
                InvocationExample.of("https://api.github.com/orgs/FAKE/members", "org_members.json"),
                InvocationExample.of("https://api.github.com/graphql", "graph_api_users_saml.json"),
                InvocationExample.of("https://api.github.com/graphql", "graph_api_users_members.json"),
                InvocationExample.of("https://api.github.com/graphql", "graph_api_error.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/teams", "org_teams.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/teams/TEAM/members", "team_members.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/audit-log?phrase=action:copilot&include=web", "org_audit_log.json"),
                InvocationExample.of("https://api.github.com/organizations/123456789/audit-log?include=web&per_page=100&phrase=action:copilot+created:2023-02-16T12:00:00%2B0000..2023-04-17T00:00:00%2B0000&page=0&order=asc&after=MS42OEQyOTE2MjX1MqNlJzIyfANVOHoYbUVsZ1ZjUWN6TwlZLXl6EVE%3D&before", "org_audit_log.json"),
                InvocationExample.of("https://api.github.com/orgs/FAKE/copilot/billing/seats", "org_copilot_seats.json")
        );
    }
}
