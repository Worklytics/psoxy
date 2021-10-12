package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Rules;
import co.worklytics.psoxy.rules.RulesTest;
import co.worklytics.test.TestUtils;
import com.google.api.client.http.GenericUrl;
import lombok.Getter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GDriveTests extends RulesTest {

    @Getter
    final Rules rulesUnderTest = PrebuiltSanitizerRules.GDRIVE;

    @Getter
    final String exampleDirectoryPath = "api-response-examples/g-workspace/gdrive-v2";

    @Test
    void files() {
        String jsonString = asJson("files.json");

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("Bob@worklytics.co"));
        assertTrue(jsonString.contains("alice@worklytics.co"));

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://www.googleapis.com/drive/v2/files"), jsonString);

        assertFalse(sanitized.contains("alice@worklytics.co"));
        assertFalse(sanitized.contains("Bob@worklytics.co"));
    }

    @Test
    void revisions() {
        String jsonString = asJson("revisions.json");

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("Bob@worklytics.co"));

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://www.googleapis.com/drive/v2/files/Asdfasdfas/revisions"), jsonString);

        assertFalse(sanitized.contains("Bob@worklytics.co"));

    }
}
