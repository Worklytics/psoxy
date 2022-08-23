package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RuleSet;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectoryTests extends JavaRulesTestBaseCase {

    @Getter
    final RuleSet rulesUnderTest = PrebuiltSanitizerRules.GDIRECTORY;

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

        Collection<String> PIItoRedact = Arrays.asList(
            "alice.example@gmail.com",
            "https://intranet.worklytics.co/alice",
            "https://about.me/alice_example",
            "/home/alice_worklytics_co", // posix accounts
            "ssh-rsa" // ssh keys
        );
        // primaryEmail, emails, relations, aliases and nonEditableAliases
        Collection<String> PIItoPseudonymize = Arrays.asList(
            "a@in.worklytics.co",
            "a@worklytics.co",
            "a@worklytics.co.test-google-a.com",
            "aexample@in.worklytics.co",
            "aexample@worklytics.co",
            "aexample@worklytics.co.test-google-a.com",
            "alice.example@in.worklytics.co",
            "alice.example@worklytics.co",
            "alice.example@worklytics.co.test-google-a.com",
            "alice@in.worklytics.co",
            "alice@worklytics.co",
            "alice@worklytics.co.test-google-a.com",
            "alise@in.worklytics.co",
            "alise@worklytics.co",
            "alise@worklytics.co.test-google-a.com",
            "bob@worklytics.co"
        );
        assertNotSanitized(jsonString, PIItoRedact);
        assertNotSanitized(jsonString, PIItoPseudonymize);

        String sanitized =
            sanitizer.sanitize(new URL("https", "admin.googleapis.com", "/admin/directory/v1/users/123213"), jsonString);

        assertPseudonymized(sanitized, PIItoPseudonymize);
        assertRedacted(sanitized, PIItoRedact);
    }

    @SneakyThrows
    @Test
    void users() {
        String jsonString = asJson("users.json");

        Collection<String> PIItoRedact = Arrays.asList(
            "alice.example@gmail.com", // recovery email
            "https://intranet.worklytics.co/alice",
            "https://about.me/alice_example",
            "/home/alice_worklytics_co", // posix accounts
            "ssh-rsa" // ssh keys
        );
        // primaryEmail, emails, aliases and nonEditableAliases
        Collection<String> PIItoPseudonymize = Arrays.asList(
            "alice.example@worklytics.co.test-google-a.com",
            "alice@worklytics.co.test-google-a.com",
            "aexample@worklytics.co.test-google-a.com",
            "a@worklytics.co.test-google-a.com",
            "alise@worklytics.co.test-google-a.com",
            "aexample@in.worklytics.co",
            "alice@in.worklytics.co",
            "alice.example@in.worklytics.co",
            "alise@in.worklytics.co",
            "a@in.worklytics.co",
            "alice.example@worklytics.co",
            "aexample@worklytics.co",
            "a@worklytics.co",
            "alise@worklytics.co",
            "bob@worklytics.co",
            "bob@worklytics.co.test-google-a.com",
            "bob@in.worklytics.co"
        );
        assertNotSanitized(jsonString, PIItoRedact);
        assertNotSanitized(jsonString, PIItoPseudonymize);

        String endpoint = "https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer";
        String sanitized = sanitize(endpoint, jsonString);

        assertPseudonymized(sanitized, PIItoPseudonymize);
        assertRedacted(sanitized, PIItoRedact);
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

        //NOTE: used to have test that thumbnail data redacted EVEN if request allowed through, but
        // with Rules2 format this doesn't make sense; rules are based on the matched endpoint, so
        // if no rules ALLOW thumbnails, by definition no rules will REDACT its content either
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


    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://admin.googleapis.com/admin/directory/v1/groups/any-group-id/members", "group-members.json"),
            InvocationExample.of("https://admin.googleapis.com/admin/directory/v1/users/123431234", "user.json"),
            InvocationExample.of("https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer&maxResults=1&pageToken=BASE64TOKEN-=%3D&viewType=admin_view", "users.json")
        );
    }

}
