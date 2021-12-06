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

public class GDriveTests extends RulesBaseTestCase {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GDRIVE;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/gdrive-v2";

    @Getter
    final String defaultScopeId = "gapps";


    @SneakyThrows
    @Test
    void files() {
        String endpoint = "https://www.googleapis.com/drive/v2/files";
        String jsonString = asJson("files.json");

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("Bob@worklytics.co"));
        assertTrue(jsonString.contains("alice@worklytics.co"));

        Collection<String> PII = Arrays.asList(
            "alice@worklytics.co",
            "Bob@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);


        String sanitized =
            sanitizer.sanitize(new URL(endpoint), jsonString);

        assertPseudonymized(sanitized, PII);

        assertUrlWithQueryParamsAllowed(endpoint);
    }

    @SneakyThrows
    @Test
    void revisions() {
        String endpoint = "http://www.googleapis.com/drive/v2/files/any-file-id/revisions";
        String jsonString = asJson("revisions.json");

        //verify precondition that example actually contains something we need to pseudonymize
        Collection<String> PII = Arrays.asList(
            "Bob@worklytics.co"
        );
        assertNotSanitized(jsonString, PII);

        String sanitized =
            sanitizer.sanitize(new URL(endpoint), jsonString);

        assertPseudonymized(sanitized, PII);

        assertUrlWithQueryParamsAllowed(endpoint);
    }


    @SneakyThrows
    @Test
    void permissions() {
        String endpoint = "http://www.googleapis.com/drive/v2/files/any-file-id/permissions";

        //TODO: content test (although in theory, utilizes $..email pattern, so should be safe)

        assertUrlWithQueryParamsAllowed(endpoint);
    }
}
