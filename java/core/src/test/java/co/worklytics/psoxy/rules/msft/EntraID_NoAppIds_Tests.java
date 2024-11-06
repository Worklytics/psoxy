package co.worklytics.psoxy.rules.msft;

import co.worklytics.psoxy.rules.Rules2;
import lombok.Getter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

public class EntraID_NoAppIds_Tests extends EntraIDTests {

    @Getter
    final Rules2 rulesUnderTest = PrebuiltSanitizerRules.ENTRA_ID_NO_MSFT_IDS;

    @Getter
    final RulesTestSpec rulesTestSpec = RulesTestSpec.builder()
        .sourceFamily("microsoft-365")
        .defaultScopeId("azure-ad")
        .sourceKind("entra-id")
        .rulesFile("entra-id_no-app-ids")
        .exampleSanitizedApiResponsesPath("example-api-responses/sanitized_no-app-ids/")
        .build();

    @ValueSource(strings = {
        "alice@acme.com",
        "4ea7fc01-0264-4e84-b85e-9e49fba4de97",
    })
    @ParameterizedTest
    void users_byId(String id) {
        String endpoint = "https://graph.microsoft.com/v1.0/users/" + id;
        assertUrlBlocked(endpoint);
    }

    @Override
    @Test
    void user() {
        String jsonString = asJson(ENTRA_ID_API_EXAMPLES_PATH, "user.json");

        String endpoint = "https://graph.microsoft.com/v1.0/users/p~2343adsfasdfa";

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
    void user_noAppIds() {
        String jsonString = asJson(ENTRA_ID_API_EXAMPLES_PATH, "user.json");

        String endpoint = "https://graph.microsoft.com/v1.0/users/p~2343adsfasdfa";

        Collection<String> PII = Arrays.asList(
            "48d31887-5fad-4d73-a9f5-3c356e68a038"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(endpoint, jsonString);

        assertReversibleUrlTokenized(sanitized, PII);
    }



    @Test
    void users_noAppIds() {
        String jsonString = asJson(ENTRA_ID_API_EXAMPLES_PATH, "users.json");

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

        String jsonString = asJson(ENTRA_ID_API_EXAMPLES_PATH, "group-members.json");

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

    @Override
    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://graph.microsoft.com/v1.0/groups/02bd9fd6-8f93-4758-87c3-1fb73740a315/members?$count=true", "group-members.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/p~JuB1uFI_rtVS0Ygtc3m4uxhEiLI-6vn5ySKma20etlGvAJvlFOlnYuRejZSdIm5tmHzio-TdKzazWRwL50vNeFravJETR0l1WAvE219Jwug?%24select=proxyAddresses%2CotherMails%2ChireDate%2CisResourceAccount%2Cmail%2CemployeeId%2Cid%2CuserType%2CmailboxSettings%2CaccountEnabled", "user.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users/p~12398123012312", "user.json"),
            InvocationExample.of("https://graph.microsoft.com/v1.0/users", "users.json")
        );
    }
}