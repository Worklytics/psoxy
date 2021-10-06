package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.test.TestUtils;
import com.google.api.client.http.GenericUrl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrebuiltSanitizerOptionsTest {


    @Test
    void google_chat() {
        SanitizerImpl sanitizer = new SanitizerImpl(Sanitizer.Options.builder()
            .rules(PrebuiltSanitizerRules.GOOGLE_CHAT).build());

        String jsonString = new String(TestUtils.getData("api-response-examples/g-workspace/google-chat/chat-activities.json"));

        //verify precondition that example actually contains something we need to pseudonymize
        assertTrue(jsonString.contains("philip@worklytics.co"));

        String sanitized =
            sanitizer.sanitize(new GenericUrl("https://admin.googleapis.com/admin/reports/v1/activity/users/all/applications/chat"), jsonString);

        assertFalse(sanitized.contains("philip@worklytics.co"));
    }
}
