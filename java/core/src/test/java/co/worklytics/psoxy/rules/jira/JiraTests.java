package co.worklytics.psoxy.rules.jira;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RESTRules;
import lombok.Getter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public class JiraTests extends JavaRulesTestBaseCase {

    @Getter
    final RESTRules rulesUnderTest = PrebuiltSanitizerRules.JIRA;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/jira";

    @Getter
    final String defaultScopeId = "jira";

    @Getter
    final String yamlSerializationFilepath = "jira/jira";

    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }

    @Test
    void users() {
        String jsonString = asJson(exampleDirectoryPath, "users.json");

        //no single-user case
        assertUrlBlocked("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/users?accountId=5b10ac8d82e05b22cc7d4ef5'");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/users?startAt=0&maxResults=25";

        Collection<String> PII = Arrays.asList("608a9b555426330072f9867d", "fake@contoso.com", "Fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "608a9b555426330072f9867d");
        assertPseudonymized(sanitized, "fake@contoso.com");
        assertRedacted(sanitized,
                "Fake", // display name
                "https://..." //photo url placeholders
        );

        //ensure we allow paging of users, and passing cloud id
        assertUrlWithQueryParamsBlocked("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/users");
    }

    @Test
    void groups_bulk() {
        String jsonString = asJson(exampleDirectoryPath, "groups.json");

        //no single-user case
        assertUrlBlocked("https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/group/bulk?groupId=5b10ac8d82e05b22cc7d4ef5'");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/group/bulk";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized, "jira-software-users", "administrators");
    }

    @Test
    void group_member() {
        String jsonString = asJson(exampleDirectoryPath, "group_member.json");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/group/member?groupId=5b10ac8d82e05b22cc7d4ef5";

        Collection<String> PII = Arrays.asList("5b10a2844c20165700ede21g", "mia@example.com", "Mia");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "5b10a2844c20165700ede21g");
        assertPseudonymized(sanitized, "mia@example.com");
        assertRedacted(sanitized,
                "Mia", // display name
                "some name", // name
                "https://..." //photo url placeholders
        );
    }

    @Test
    void issues_by_jql() {
        String jsonString = asJson(exampleDirectoryPath, "issues_by_jql.json");

        String endpoint = "https://api.atlassian.com/ex/jira/f6eef702-e05d-43ba-bd5c-75fce47d560e/rest/api/3/search?jql=something&startAt=50";

        Collection<String> PII = Arrays.asList("608a9b555426330072f9867d", "fake@contoso.com", "Fake");
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "608a9b555426330072f9867d");
        assertPseudonymized(sanitized, "fake@contoso.com");
        assertRedacted(sanitized,
                "Fake", // display name
                "https://..." //photo url placeholders
        );
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(InvocationExample.of("https://app.asana.com/api/1.0/users", "users.json"),

                InvocationExample.of("https://app.asana.com/api/1.0/workspaces/123/teams", "teams.json"),

                InvocationExample.of("https://app.asana.com/api/1.0/teams/123123/projects", "projects.json"),

                InvocationExample.of("https://app.asana.com/api/1.0/tasks?project=123123", "tasks.json"),
                InvocationExample.of("https://app.asana.com/api/1.0/tasks/123123?opt_fields=fake", "task.json"),
                InvocationExample.of("https://app.asana.com/api/1.0/tasks/123123", "task.json"),

                InvocationExample.of("https://app.asana.com/api/1.0/tasks/123123/stories", "stories.json"),
                InvocationExample.of("https://app.asana.com/api/1.0/tasks/123123/stories?opt_fields=fake", "stories.json"),

                InvocationExample.of("https://app.asana.com/api/1.0/tasks/123123/subtasks", "tasks.json"),
                InvocationExample.of("https://app.asana.com/api/1.0/tasks/123123/subtasks?opt_fields=fake", "tasks.json"),

                InvocationExample.of("https://app.asana.com/api/1.0/workspaces/123/tasks/search?modified_at.after=fake", "tasks.json"));
    }
}