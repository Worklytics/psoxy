package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.test.MockModules;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class WindsurfServiceKeyAuthStrategyTest {

    @SneakyThrows
    @Test
    void addServiceKeyToRequestBody() {
        SecretStore secretStore = MockModules.provideMock(SecretStore.class);
        when(secretStore.getConfigPropertyOrError(WindsurfServiceKeyAuthStrategy.ConfigProperty.SERVICE_KEY))
            .thenReturn("test-key");

        WindsurfServiceKeyAuthStrategy strategy = new WindsurfServiceKeyAuthStrategy(
            new ObjectMapper(),
            secretStore
        );

        String originalContent = "{\"data\":\"value\"}";
        byte[] modifiedContent = strategy.addServiceKeyToRequestBody(originalContent.getBytes());
        String modifiedContentStr = new String(modifiedContent);
        assertTrue(modifiedContentStr.contains("\"service_key\":\"test-key\""), "Service key should be added to the request body");
    }
}
