package co.worklytics.psoxy.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigServiceTest {

    @Test
    void parseIntValue_parsesValidInteger() {
        assertEquals(42, ConfigService.parseIntValue(ApiModeConfig.ApiModeConfigProperty.REQUEST_TIMEOUT_SECONDS, "42"));
    }

    @Test
    void parseIntValue_rejectsInvalidInteger() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ConfigService.parseIntValue(
                        ApiModeConfig.ApiModeConfigProperty.REQUEST_TIMEOUT_SECONDS, "not-a-number"));

        assertEquals("Invalid value for REQUEST_TIMEOUT_SECONDS: 'not-a-number'", ex.getMessage());
    }
}
