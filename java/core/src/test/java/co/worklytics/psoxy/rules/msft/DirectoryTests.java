package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

public class DirectoryTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitzerRules.DIRECTORY;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/microsoft-365/directory";

    @Getter
    final String defaultScopeId = "azure-ad";

    @Getter
    final String yamlSerializationFilepath = "microsoft-365/directory";

    @Test
    void user() {
        String jsonString = asJson("user.json");

        String endpoint = "https://graph.microsoft.com/v1.0/users/2343adsfasdfa";

        Collection<String> PII = Arrays.asList(
            "MeganB@M365x214355.onmicrosoft.com",
            "Megan",
            "Bowen",
            "Megan Bowen",
            "+1 412 555 0109"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "MeganB@M365x214355.onmicrosoft.com");
        assertRedacted(sanitized,
            "Megan",
            "Bowen",
            "Megan Bowen",
            "+1 412 555 0109"
        );

        assertUrlWithSubResourcesBlocked(endpoint);
    }

    @Test
    void users() {
        String jsonString = asJson("users.json");

        String endpoint = "https://graph.microsoft.com/v1.0/users";

        Collection<String> PII = Arrays.asList(
            "john@worklytics.onmicrosoft.com",
            "Paul Allen"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, "john@worklytics.onmicrosoft.com");
        assertRedacted(sanitized, "Paul Allen");
    }

    @Test
    void group() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315";
        assertUrlWithQueryParamsAllowed(endpoint);
    }

    @Test
    void groups() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups";
        assertUrlWithQueryParamsAllowed(endpoint);
    }

    @Test
    void groupMembers() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315/members?$count=true";

        String jsonString = asJson("group-members.json");
        assertNotSanitized(jsonString,
            "Adele Vance",
            "AdeleV@M365x214355.onmicrosoft.com",
            "Vance",
            "+1 425 555 0109",
            "+1 502 555 0144",
            "Patti Fernandez",
            "PattiF@M365x214355.onmicrosoft.com"
        );

        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized,
            "Vance",
            "+1 425 555 0109",
            "+1 502 555 0144",
            "Patti Fernandez"
        );

        assertPseudonymized(sanitized,
            "AdeleV@M365x214355.onmicrosoft.com",
            "PattiF@M365x214355.onmicrosoft.com"
            );

    }
}
