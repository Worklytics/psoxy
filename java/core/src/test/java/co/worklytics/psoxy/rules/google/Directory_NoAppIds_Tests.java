package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.RuleSet;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

public class Directory_NoAppIds_Tests extends DirectoryTests {

    @Getter
    final RuleSet rulesUnderTest = PrebuiltSanitizerRules.GDIRECTORY_WITHOUT_GOOGLE_IDS;

    @BeforeEach
    public void setTestSpec() {
        this.setTestSpec(RulesTestSpec.builder()
                .yamlSerializationFilePath("google-workspace/directory_no-app-ids")
            .sanitizedExamplesDirectoryPath("api-response-examples/g-workspace/directory/no-app-ids")
            .build());
    }


    @SneakyThrows
    @Test
    void user() {
        String jsonString = asJson("user.json");

        // primaryEmail, emails, relations, aliases and nonEditableAliases
        Collection<String> PIItoPseudonymize = Arrays.asList(
            "1234234331471490332349959"
        );
        assertNotSanitized(jsonString, PIItoPseudonymize);

        String sanitized =
            sanitizer.sanitize(new URL("https", "admin.googleapis.com", "/admin/directory/v1/users/123213"), jsonString);

        assertReversibleUrlTokenized(sanitized, PIItoPseudonymize);
    }

    @SneakyThrows
    @Test
    void users_noAppIds() {
        String jsonString = asJson("users.json");

        // primaryEmail, emails, aliases and nonEditableAliases
        Collection<String> PIItoPseudonymize = Arrays.asList(
            "1234234331471490332349959"
        );
        assertNotSanitized(jsonString, PIItoPseudonymize);

        String endpoint = "https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer";
        String sanitized = sanitize(endpoint, jsonString);

        assertReversibleUrlTokenized(sanitized, PIItoPseudonymize);
    }

    @SneakyThrows
    @Test
    void groupMembers_noAppIds() {

        String membersEndpoint = "https://admin.googleapis.com/admin/directory/v1/groups/any-group-id/members";
        String jsonString = asJson("group-members.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alex@acme.com",
            "dan@acme.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize(membersEndpoint, jsonString);

        assertPseudonymized(sanitized, Arrays.asList("alex@acme.com", "dan@acme.com"));
        assertUrlWithQueryParamsAllowed(membersEndpoint);
    }

}
