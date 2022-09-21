package co.worklytics.psoxy.rules.asana;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RuleSet;
import lombok.Getter;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public class AsanaTests extends JavaRulesTestBaseCase {

    @Getter
    final RuleSet rulesUnderTest = PrebuiltSanitizerRules.ASANA;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/asana";

    @Getter
    final String defaultScopeId = "asana";

    @Getter
    final String yamlSerializationFilepath = "asana/asana";

    @Disabled // not reliable; seems to have different value via IntelliJ/AWS deployment and my
    // laptop's maven, which doesn't make any sense, given that binary deployed to AWS was built via
    // maven on my laptop - so something about Surefire/Junit runner used by maven??
    @Test
    void sha() {
        this.assertSha("7869e465607b7a00b4bd75a832a9ed1f811ce7f2");
    }


    @Test
    void workspaces() {
       //String jsonString = asJson(exampleDirectoryPath, "users.json");

        String endpoint = "https://app.asana.com/api/1.0/workspaces";
        assertUrlAllowed(endpoint);
        assertUrlWithQueryParamsAllowed(endpoint);
        //nothing sanitized from this for now
    }

    @Test
    void users() {
        String jsonString = asJson(exampleDirectoryPath, "users.json");

        //no single-user case
        assertUrlBlocked("https://app.asana.com/api/1.0/users/123123");

        String endpoint = "https://app.asana.com/api/1.0/users";

        Collection<String> PII = Arrays.asList(
            "gsanchez@example.com",
            "Greg Sanchez"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "gsanchez@example.com");
        assertRedacted(sanitized,
            "Greg Sanchez",
            "https://..." //photo url placeholders
        );

        //ensure we allow paging of users, and passing workspaceId
        assertUrlWithQueryParamsAllowed(endpoint);
    }

    @Test
    void teams() {
        String jsonString = asJson(exampleDirectoryPath, "teams.json");

        String endpoint = "https://app.asana.com/api/1.0/workspaces/123123/teams";

        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized,
            "developers should be members of this team.",
            "Marketing"
        );
        assertUrlWithQueryParamsAllowed(endpoint);
        assertUrlWithSubResourcesBlocked(endpoint);
    }


    @Test
    void projects() {
        String jsonString = asJson(exampleDirectoryPath, "projects.json");

        String endpoint = "https://app.asana.com/api/1.0/teams/123123/projects";

        Collection<String> PII = Arrays.asList(
            "Greg Sanchez"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized,
            "Greg Sanchez",
            "gsanchez@example.com",
            "Marketing",
            "Stuff to buy",
            "These are things we need to purchase",
            "The project is moving forward",
            "Status Update"
        );

        assertUrlWithQueryParamsAllowed(endpoint);
    }

    @Test
    void tasks() {
        String jsonString = asJson(exampleDirectoryPath, "tasks.json");

        String endpoint = "https://app.asana.com/api/1.0/projects/123123/tasks";

        Collection<String> PII = Arrays.asList(
            "Greg Sanchez"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized,
            "A blob of information",
            "likes the stuff from Humboldt",
            "Greg Sanchez",
            "gsanchez@example.com",
            "Marketing",
            "Stuff to buy"
        );
    }

    @Test
    void stories() {
        String jsonString = asJson(exampleDirectoryPath, "stories.json");

        String endpoint = "https://app.asana.com/api/1.0/tasks/123123/stories";

        Collection<String> PII = Arrays.asList(
            "Greg Sanchez"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized,
            "Greg Sanchez",
            "gsanchez@example.com",
            "Marketing",
            "Stuff to buy",
            "This was the Old Text",
            "This is the New Text",
            "This is the New Name",
            "This was the Old Name",
            "Great! I like this idea.",
            "This was the Old Text",
            "This is a comment."
        );
    }



    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://app.asana.com/api/1.0/users", "users.json"),

            InvocationExample.of("https://app.asana.com/api/1.0/workspaces/123/teams", "teams.json"),

            InvocationExample.of("https://app.asana.com/api/1.0/teams/123123/projects", "projects.json"),

            InvocationExample.of("https://app.asana.com/api/1.0/projects/123123/tasks", "tasks.json"),

            InvocationExample.of("https://app.asana.com/api/1.0/tasks/123123/stories", "stories.json")
        );
    }

}
