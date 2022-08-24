package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import co.worklytics.psoxy.rules.Transform;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;

public class Directory_NoAppIds_Tests extends DirectoryTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.DIRECTORY_NO_MSFT_IDS;

    @BeforeEach
    public void setTestSpec() {
        this.setTestSpec(RulesTestSpec.builder()
                .yamlSerializationFilePath("microsoft-365/directory_no-app-ids")
                .sanitizedExamplesDirectoryPath("api-response-examples/microsoft-365/directory/no-app-ids")
                .build());
    }

    @Test
    void user_noAppIds() {
        String jsonString = asJson(exampleDirectoryPath, "user.json");

        String endpoint = "https://graph.microsoft.com/v1.0/users/2343adsfasdfa";

        Collection<String> PII = Arrays.asList(
            "48d31887-5fad-4d73-a9f5-3c356e68a038"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertReversibleUrlTokenized(sanitized, PII);
    }

    @Test
    void users_noAppIds() {
        String jsonString = asJson(exampleDirectoryPath, "users.json");

        String endpoint = "https://graph.microsoft.com/v1.0/users";

        Collection<String> PII = Arrays.asList(
            "f8f2864c-c353-4471-bd75-9e49fba4de97",
            "219435cc-746e-48d6-8ccd-242345234f6e",
            "4ea7fc01-0264-4e84-b85e-9e49fba4de97",
            "cdfb873d-61f9-4656-bbbb-9e49fba4de97",
            "307ee26b-fe96-40a9-bc8a-9e49fba4de97",
            "232442306-9942-4750-a708-4a1e4fe8a879"
        );

        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertReversibleUrlTokenized(sanitized, PII);
    }

    @Test
    void groupMembers_noAppIds() {
        String endpoint = "https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315/members?$count=true";

        String jsonString = asJson(exampleDirectoryPath, "group-members.json");

        Collection<String> PII = Arrays.asList(
            "87d349ed-44d7-43e1-9a83-5f2406dee5bd",
            "626cbf8c-5dde-46b0-8385-9e40d64736fe",
            "40079818-3808-4585-903b-02605f061225",
            "074e56ea-0b50-4461-89e5-c67ae14a2c0b",
            "16cfe710-1625-4806-9990-91b8f0afee35",
            "089a6bb8-e8cb-492c-aa41-c078aa0b5120"
        );

        String sanitized = this.sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, PII);
    }
}
