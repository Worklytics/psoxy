package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.rules.JavaRulesTestBaseCase;
import co.worklytics.psoxy.rules.RuleSet;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;


public class GDriveV3Tests extends JavaRulesTestBaseCase {

    @Getter
    final RuleSet rulesUnderTest = PrebuiltSanitizerRules.GDRIVE;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/gdrive-v3";

    @Getter
    final String defaultScopeId = "gapps";


    @Getter
    final String yamlSerializationFilepath = "google-workspace/gdrive";


    @SneakyThrows
    @Test
    void files() {
        String jsonString = asJson("files.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "paul@worklytics.co",
            "alice@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);


        String sanitized =
            sanitizer.sanitize(new URL("https", "www.googleapis.com", "/drive/v3/files"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Alice", "Paul");
        assertRedacted(sanitized, "File Name",
            "Alice", "Paul"
            );
    }

    @SneakyThrows
    @Test
    void revisions() {
        String jsonString = asJson("revisions.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "paul@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("http://www.googleapis.com/drive/v3/files/any-file-id/revisions"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Alice", "Paul");
    }

    @SneakyThrows
    @Test
    void revision() {
        String jsonString = asJson("revision.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "paul@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL("http://www.googleapis.com/drive/v3/files/any-file-id/revisions/any-revision-id"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Alice", "Paul");
    }

    @SneakyThrows
    @Test
    void file_permissions() {
        String jsonString = asJson("permissions.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);
        assertNotSanitized(jsonString, "Alice");

        String sanitized =
            sanitizer.sanitize(new URL("http://www.googleapis.com/drive/v3/files/some-file-id/permissions"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Alice");
    }


    @SneakyThrows
    @Test
    void file_permission() {
        String jsonString = asJson("permission.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);
        assertNotSanitized(jsonString, "Alice");

        String sanitized =
            sanitizer.sanitize(new URL("http://www.googleapis.com/drive/v3/files/some-file-id/permissions/234234"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Alice");
    }

    public Stream<InvocationExample> getExamples() {
        return Stream.of(
            InvocationExample.of("https://www.googleapis.com/drive/v3/files", "files.json"),
            InvocationExample.of("http://www.googleapis.com/drive/v3/files/some-file-id/permissions/234234", "permission.json"),
            InvocationExample.of("http://www.googleapis.com/drive/v3/files/some-file-id/permissions", "permissions.json"),
            InvocationExample.of("http://www.googleapis.com/drive/v3/files/any-file-id/revisions", "revisions.json")
        );
    }
}
