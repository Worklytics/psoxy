package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectoryTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GDIRECTORY;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/directory";

    @Getter
    final String defaultScopeId = "gapps";


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

        assertPseudonymized(sanitized, Arrays.asList("alice@worklytics.co"));
        assertRedacted(sanitized, Arrays.asList("alice.example@gmail.com"));
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

        String sanitized =
            sanitizer.sanitize(new URL("https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer"), jsonString);

        assertPseudonymized(sanitized, Arrays.asList("alice@worklytics.co", "bob@worklytics.co"));
        assertRedacted(sanitized, Arrays.asList("alice.example@gmail.com"));
    }

    @SneakyThrows
    @Test
    void groups() {
        String jsonString = asJson("groups.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "Users allowed to have access to production infrastructure."
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("https://admin.googleapis.com/admin/directory/v1/groups?customer=my_customer"), jsonString);

        assertRedacted(sanitized, Arrays.asList("Users allowed to have access to production infrastructure."));

        assertPseudonymizedWithOriginal(sanitized, "admins@aceme.com", "admins@in.aceme.com");
    }

    @SneakyThrows
    @Test
    void group() {
        String jsonString = asJson("group.json");

        assertNotSanitized(jsonString, Arrays.asList("Anyone sales person in our organization."));
        String sanitized =
            sanitizer.sanitize(new URL("https://admin.googleapis.com/admin/directory/v1/groups/asdfas"), jsonString);

        assertRedacted(sanitized, Arrays.asList("Anyone sales person in our organization."));

        assertPseudonymizedWithOriginal(sanitized, "sales@acme.com", "sales@in.acme.com");
    }

    @SneakyThrows
    @Test
    void groupMembers() {

        String jsonString = asJson("group-members.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alex@acme.com",
            "dan@acme.com"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("https://admin.googleapis.com/admin/directory/v1/groups/asdfas/members"), jsonString);

        assertPseudonymized(sanitized, Arrays.asList("alex@acme.com", "dan@acme.com"));
    }

    @SneakyThrows
    @Test
    void thumbnails() {
        String jsonString = asJson("thumbnail.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);
        assertNotSanitized(jsonString, Arrays.asList("photoData"));

        URL url =
            new URL("https://admin.googleapis.co/admin/directory/v1/users/123124/photos/thumbnail");

        //block by default
        assertThrows(IllegalStateException.class, () -> sanitizer.sanitize(url, jsonString));

        Rules allowAllRoles = getRulesUnderTest().toBuilder().allowedEndpointRegex(".*").build();

        this.sanitizer = new SanitizerImpl(Sanitizer.Options.builder().pseudonymizationSalt("salt")
            .rules(allowAllRoles)
            .defaultScopeId("gapps").build());
        this.sanitizer.initConfiguration();

        //but still redact if gets through
        String sanitized = this.sanitizer.sanitize(url, jsonString);
        assertRedacted(sanitized, "alice@worklytics.co");
        assertRedacted(sanitized, "photoData");
    }

    @SneakyThrows
    @Test
    public void roles() {
        String jsonString = asJson("roles.json");

        String endpoint = "https://admin.googleapis.co/admin/directory/v1/customer/my_customer/roles";
        assertNotSanitized(jsonString, "Google Apps Administrator Seed Role");

        String sanitized = this.sanitizer.sanitize(new URL(endpoint), jsonString);

        assertRedacted(sanitized, "Google Apps Administrator Seed Role");
    }
}
