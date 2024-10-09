package co.worklytics.psoxy.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ProxyConfigPropertyTest {



    @ValueSource(
        strings = {
            "PSOXY_ENCRYPTION_KEY",
            "ENCRYPTION_KEY_IP",
            "RULES",
            "PSOXY_SALT",
            "SALT_IP",
        }
    )
    @ParameterizedTest
    public void remoteConfigVars(String paramName) {
    }

    @ValueSource(
        strings = {
            "CUSTOM_RULES_SHA",
            "EMAIL_CANONICALIZATION",
            "PATH_TO_SHARED_CONFIG",
            "PATH_TO_INSTANCE_CONFIG",
            "IDENTIFIER_SCOPE_ID",
        }
    )
    @ParameterizedTest
    public void envOnlyConfigVars(String paramName) {
        ProxyConfigProperty property = ProxyConfigProperty.valueOf(paramName);
        assertNotNull(property);
        assertTrue(property.isEnvVarOnly());
    }


}
