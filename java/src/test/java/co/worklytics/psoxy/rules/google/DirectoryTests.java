package co.worklytics.psoxy.rules.google;

import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.test.TestUtils;
import com.google.api.client.http.GenericUrl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DirectoryTests {
    SanitizerImpl sanitizer;

    @BeforeEach
    public void setup() {
        sanitizer = new SanitizerImpl(Sanitizer.Options.builder()
            .rules(PrebuiltSanitizerRules.GDIRECTORY)
            .build());
    }

    @Test
    void validate() {
        PrebuiltSanitizerRules.GDIRECTORY.validate();

    }

    @Test
    void user() {
        String jsonString = new String(TestUtils.getData("api-response-examples/g-workspace/directory/user.json"));

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("alice@worklytics.co"));

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://admin.googleapis.com/admin/directory/v1/users/123213"), jsonString);

        assertFalse(sanitized.contains("alice@worklytics.co"));
        assertFalse(sanitized.contains("alice.example@gmail.com"));
    }

    @Test
    void users() {
        String jsonString = new String(TestUtils.getData("api-response-examples/g-workspace/directory/users.json"));

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("alice@worklytics.co"));
        assertTrue(jsonString.contains("bob@worklytics.co"));

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://admin.googleapis.com/admin/directory/v1/users?customer=my_customer"), jsonString);

        assertFalse(sanitized.contains("alice@worklytics.co"));
        assertFalse(sanitized.contains("alice.example@gmail.com"));
        assertFalse(sanitized.contains("bob@worklytics.co"));
    }
}
