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
    void user() {
        String jsonString = asJson(exampleDirectoryPath, "user.json");

        String endpoint = "https://app.asana.com/api/1.0/users/sdfgsdfg";

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

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    void users() {
        String jsonString = asJson(exampleDirectoryPath, "users.json");

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
    }

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://app.asana.com/api/1.0/users/12341234", "user.json"),
            InvocationExample.of("https://app.asana.com/api/1.0/users", "users.json")
        );
    }

}
