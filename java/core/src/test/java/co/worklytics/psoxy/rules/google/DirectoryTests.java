package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectoryTests extends JavaRulesTestBaseCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GDIRECTORY;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/directory";

    @Getter
    final String defaultScopeId = "gapps";


    @Getter
    final String yamlSerializationFilepath = "google-workspace/directory";


    @SneakyThrows
    @Test
    void user() {
        String jsonString = asJson("user.json");

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("alice@worklytics.co"));

        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co",
            "alice.example@gmail.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("https", "admin.googleapis.com", "/admin/directory/v1/users/123213"), jsonString);

        assertPseudonymized(sanitized, List.of("alice@worklytics.co"));
        assertRedacted(sanitized, List.of("alice.example@gmail.com"));
    }

    @SneakyThrows
    @Test
    void users() {
        String jsonString = asJson("users.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co",
            "bob@worklytics.co",
            "alice.example@gmail.com"
        );
        assertNotSanitized(jsonString, PII);

        String endpoint = "https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer";
        String sanitized = sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, Arrays.asList("alice@worklytics.co", "bob@worklytics.co"));
        assertRedacted(sanitized, List.of("alice.example@gmail.com"));
    }

    @SneakyThrows
    @Test
    void groups() {
        String jsonString = asJson("groups.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = List.of(
            "Users allowed to have access to production infrastructure."
        );
        assertNotSanitized(jsonString, PII);

        String sanitized = this.sanitize("https://admin.googleapis.com/admin/directory/v1/groups?customer=my_customer", jsonString);

        assertRedacted(sanitized, List.of("Users allowed to have access to production infrastructure."));

        assertPseudonymizedWithOriginal(sanitized, "admins@aceme.com", "admins@in.aceme.com");
    }

    @SneakyThrows
    @Test
    void group() {
        String groupEndpoint = "https://admin.googleapis.com/admin/directory/v1/groups/an-arbitrary-subresource";
        String jsonString = asJson("group.json");

        assertNotSanitized(jsonString, List.of("Anyone sales person in our organization."));
        String sanitized = this.sanitize(groupEndpoint, jsonString);

        assertRedacted(sanitized, List.of("Anyone sales person in our organization."));

        assertPseudonymizedWithOriginal(sanitized, "sales@acme.com", "sales@in.acme.com");

        assertUrlWithSubResourcesAllowed(groupEndpoint);
        assertUrlWithQueryParamsAllowed(groupEndpoint);
    }

    @SneakyThrows
    @Test
    void groupMembers() {

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

    @SneakyThrows
    @Test
    void thumbnails() {
        String jsonString = asJson("thumbnail.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = List.of(
            "alice@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);
        assertNotSanitized(jsonString, List.of("photoData"));

        String endpoint = "https://admin.googleapis.com/admin/directory/v1/users/123124/photos/thumbnail";

        //block by default
        assertThrows(IllegalStateException.class, () -> this.sanitize(endpoint, jsonString));

        Rules allowAllRoles = getRulesUnderTest().toBuilder().allowedEndpointRegex(".*").build();

        this.sanitizer = this.sanitizerFactory.create(Sanitizer.Options.builder().pseudonymizationSalt("salt")
            .rules(allowAllRoles)
            .defaultScopeId("gapps").build());

        //but still redact if gets through
        String sanitized = this.sanitize(endpoint, jsonString);
        assertRedacted(sanitized, "alice@worklytics.co");
        assertRedacted(sanitized, "photoData");
    }

    @SneakyThrows
    @Test
    public void roles() {
        String jsonString = asJson("roles.json");

        String endpoint = "https://admin.googleapis.com/admin/directory/v1/customer/my_customer/roles";
        assertNotSanitized(jsonString, "Google Apps Administrator Seed Role");

        String sanitized = this.sanitize(endpoint, jsonString);

        assertRedacted(sanitized, "Google Apps Administrator Seed Role");
    }


    @ValueSource(strings = {
        "https://admin.googleapis.com/admin/directory/v1/customer/my_customer/domains",
        "https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer&maxResults=1&pageToken=BASE64TOKEN-=%3D&viewType=admin_view",
        "https://admin.googleapis.com/admin/directory/v1/users/some@example.com",
        "https://admin.googleapis.com/admin/directory/v1/groups",
        "https://admin.googleapis.com/admin/directory/v1/groups?a=b,c&b=c",
        "https://admin.googleapis.com/admin/directory/v1/orgunits",
        "https://admin.googleapis.com/admin/directory/v1/orgunits?a=b,c&b=c",
    })
    @ParameterizedTest
    @SneakyThrows
    public void allowedEndpoints(String endpoint) {
        assertUrlWithQueryParamsAllowed(endpoint);
    }
}
