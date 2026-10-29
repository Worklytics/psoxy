package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GcpEnvironmentTest {

    @Test
    void modeConfigs_defaultClientIpOffsetWhenUnsetOrBlank() {
        assertEquals(GcpEnvironment.DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET, apiOffset(null));
        assertEquals(GcpEnvironment.DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET, apiOffset("  "));
        assertEquals(GcpEnvironment.DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET, apiOffset("-1"));
        assertEquals(GcpEnvironment.DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET, webhookOffset(null));
        assertEquals(GcpEnvironment.DEFAULT_CLIENT_IP_X_FORWARDED_FOR_OFFSET, webhookOffset("  "));
    }

    @Test
    void modeConfigs_readNegativeClientIpOffset() {
        assertEquals(-2, apiOffset("-2"));
        assertEquals(-2, webhookOffset("-2"));
    }

    @Test
    void modeConfigs_rejectNonNegativeAndNonNumericClientIpOffset() {
        assertThrows(IllegalArgumentException.class, () -> apiOffset("1"));
        assertThrows(IllegalArgumentException.class, () -> webhookOffset("nope"));
    }

    private static int apiOffset(String value) {
        ConfigService source = configReturning(value);
        ConfigService composite = CompositeConfigService.builder()
            .preferred(source)
            .fallback(source)
            .build();
        return GcpEnvironment.GcpApiModeConfig.fromConfigService(composite).getClientIpXForwardedForOffset();
    }

    private static int webhookOffset(String value) {
        return GcpEnvironment.GcpWebhookCollectorModeConfig.fromConfigService(configReturning(value))
            .getClientIpXForwardedForOffset();
    }

    private static ConfigService configReturning(String offset) {
        return new ConfigService() {
            @Override
            public String getConfigPropertyOrError(ConfigProperty property) {
                return getConfigPropertyAsOptional(property).orElseThrow();
            }

            @Override
            public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
                if ("CLIENT_IP_X_FORWARDED_FOR_OFFSET".equals(property.name()) && offset != null) {
                    return Optional.of(offset);
                }
                if ("SERVICE_URL".equals(property.name())) {
                    return Optional.of("https://example.run.app");
                }
                if ("BATCH_MERGE_SUBSCRIPTION".equals(property.name())) {
                    return Optional.of("projects/p/subscriptions/s");
                }
                return Optional.empty();
            }
        };
    }
}
