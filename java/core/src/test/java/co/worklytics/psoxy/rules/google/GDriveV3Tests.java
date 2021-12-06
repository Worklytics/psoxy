package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesBaseTestCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GDriveV3Tests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GDRIVE;

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
            sanitizer.sanitize(new URL("http://www.googleapis.com/drive/v3/files/Asdfasdfas/revisions"), jsonString);

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
            sanitizer.sanitize(new URL("http://www.googleapis.com/drive/v3/files/Asdfasdfas/revision/asdfasdf"), jsonString);

        assertPseudonymized(sanitized, PII);
        assertRedacted(sanitized, "Alice", "Paul");
    }
}
