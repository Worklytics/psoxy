package co.worklytics.psoxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

public class GcpEnvironmentTest {


    @Test
    public void testApiModeConfig() {
        // allow theses via env OR remote config (eg, secrets manager)
        assertFalse(GcpEnvironment.ApiModeConfig.ApiModeConfigProperty.SERVICE_URL.isEnvVarOnly());
        assertFalse(GcpEnvironment.ApiModeConfig.ApiModeConfigProperty.PUB_SUB_TOPIC.isEnvVarOnly());
    }
}
