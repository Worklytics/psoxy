package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientIpConfigTest {

    @Test
    void fromConfigService_defaultsWhenUnsetOrBlank() {
        assertEquals(GcpEnvironment.ClientIpConfig.DEFAULT_X_FORWARDED_FOR_OFFSET,
            GcpEnvironment.ClientIpConfig.fromConfigService(configReturning(null)).getXForwardedForOffset());
        assertEquals(GcpEnvironment.ClientIpConfig.DEFAULT_X_FORWARDED_FOR_OFFSET,
            GcpEnvironment.ClientIpConfig.fromConfigService(configReturning("  ")).getXForwardedForOffset());
        assertEquals(GcpEnvironment.ClientIpConfig.DEFAULT_X_FORWARDED_FOR_OFFSET,
            GcpEnvironment.ClientIpConfig.fromConfigService(configReturning("-1")).getXForwardedForOffset());
    }

    @Test
    void fromConfigService_readsNegativeOffset() {
        assertEquals(-2,
            GcpEnvironment.ClientIpConfig.fromConfigService(configReturning("-2")).getXForwardedForOffset());
    }

    @Test
    void fromConfigService_rejectsNonNegativeAndNonNumeric() {
        assertThrows(IllegalArgumentException.class,
            () -> GcpEnvironment.ClientIpConfig.fromConfigService(configReturning("1")));
        assertThrows(IllegalArgumentException.class,
            () -> GcpEnvironment.ClientIpConfig.fromConfigService(configReturning("nope")));
    }

    private static ConfigService configReturning(String value) {
        return new ConfigService() {
            @Override
            public String getConfigPropertyOrError(ConfigProperty property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
                if (property == GcpEnvironment.ClientIpConfig.ClientIpConfigProperty.CLIENT_IP_X_FORWARDED_FOR_OFFSET
                    && value != null) {
                    return Optional.of(value);
                }
                return Optional.empty();
            }
        };
    }
}
