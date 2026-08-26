package co.worklytics.psoxy.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LogSanitizationUtilsTest {

    @Test
    public void redactPotentialPii_null() {
        assertNull(LogSanitizationUtils.redactPotentialPii(null));
    }

    @Test
    public void redactPotentialPii_msftGraphCopilotError() {
        String input = "{\"error\":{\"code\":\"Forbidden\",\"message\":\"User "
                + "'d290f1ee-6c54-4b01-90e6-d701748f0851' does not have a valid Copilot "
                + "license.\"}}";

        String redacted = LogSanitizationUtils.redactPotentialPii(input);

        assertEquals("{\"error\":{\"code\":\"Forbidden\",\"message\":\"User "
                + "'{redacted-uuid}' does not have a valid Copilot license.\"}}", redacted);
    }

    @Test
    public void redactPotentialPii_email() {
        assertEquals("user '{redacted-email}' not found",
                LogSanitizationUtils.redactPotentialPii("user 'jane.doe@example.com' not found"));
    }

    @Test
    public void redactPotentialPii_noMatches_unchanged() {
        String input = "ERROR at Row:1:Column:136 No such column 'Ownership' on entity 'Account'";
        assertEquals(input, LogSanitizationUtils.redactPotentialPii(input));
    }
}
