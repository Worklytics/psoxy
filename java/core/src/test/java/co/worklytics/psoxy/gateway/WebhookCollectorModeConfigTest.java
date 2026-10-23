package co.worklytics.psoxy.gateway;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookCollectorModeConfigTest {

    @Test
    void collectionEnabledDefaultsFalse() {
        assertFalse(WebhookCollectorModeConfig.builder().build().getCollectionEnabled());
        assertFalse(WebhookCollectorModeConfig.fromConfigService(configWith(null)).getCollectionEnabled());
    }

    @Test
    void collectionEnabledParsesEnvVar() {
        assertTrue(WebhookCollectorModeConfig.fromConfigService(configWith("true")).getCollectionEnabled());
        assertFalse(WebhookCollectorModeConfig.fromConfigService(configWith("false")).getCollectionEnabled());
    }

    private static ConfigService configWith(String collectionEnabled) {
        return new ConfigService() {
            @Override
            public String getConfigPropertyOrError(ConfigProperty property) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
                if (collectionEnabled != null
                    && property == WebhookCollectorModeConfig.WebhookCollectorModeConfigProperty.COLLECTION_ENABLED) {
                    return Optional.of(collectionEnabled);
                }
                return Optional.empty();
            }
        };
    }
}
