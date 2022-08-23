package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.Transform;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class Directory_NoAppIds_Tests extends DirectoryTests {

    @Getter
    final RuleSet rulesUnderTest = PrebuiltSanitizerRules.GDIRECTORY_WITHOUT_GOOGLE_IDS;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/directory";


    @Getter
    final String defaultScopeId = "gapps";

    @Getter
    final String yamlSerializationFilepath = "google-workspace/directory_no-app-ids";

    @BeforeEach
    public void setTestSpec() {
        this.setTestSpec(RulesTestSpec.builder()
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

        assertTransformed(sanitized, PIItoPseudonymize, Transform.Pseudonymize.builder().includeReversible(true).build());
    }

    @SneakyThrows
    @Test
    void users() {
        String jsonString = asJson("users.json");

        // primaryEmail, emails, aliases and nonEditableAliases
        Collection<String> PIItoPseudonymize = Arrays.asList(
            "1234234331471490332349959"
        );
        assertNotSanitized(jsonString, PIItoPseudonymize);

        String endpoint = "https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer";
        String sanitized = sanitize(endpoint, jsonString);

        assertTransformed(sanitized, PIItoPseudonymize, Transform.Pseudonymize.builder().includeReversible(true).build());
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


    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://admin.googleapis.com/admin/directory/v1/groups/any-group-id/members", "group-members.json"),
            InvocationExample.of("https://admin.googleapis.com/admin/directory/v1/users/123431234", "user.json"),
            InvocationExample.of("https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer&maxResults=1&pageToken=BASE64TOKEN-=%3D&viewType=admin_view", "users.json")
        );
    }

}
