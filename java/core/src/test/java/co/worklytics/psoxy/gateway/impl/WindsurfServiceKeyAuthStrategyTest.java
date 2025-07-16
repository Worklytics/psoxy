package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.test.MockModules;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class WindsurfServiceKeyAuthStrategyTest {

    @SneakyThrows
    @ParameterizedTest
    @CsvSource({
        "{\"key\":\"value\"},key",
        "{},",
        "{\"nested\":{\"another_key\":\"another_value\"}},nested"
    })
    void addServiceKeyToRequestBody(String originalContent, String expectedKey) {
        SecretStore secretStore = MockModules.provideMock(SecretStore.class);
        when(secretStore.getConfigPropertyOrError(WindsurfServiceKeyAuthStrategy.ConfigProperty.SERVICE_KEY))
            .thenReturn("test-key");

        ObjectMapper objectMapper = new ObjectMapper();
        WindsurfServiceKeyAuthStrategy strategy = new WindsurfServiceKeyAuthStrategy(
            objectMapper,
            secretStore
        );


        Map<String, Object> parsed = objectMapper.readValue(originalContent, Map.class);
        if (expectedKey != null) {
            assertTrue(parsed.containsKey(expectedKey), "Original content should contain the expected key");
        }

        byte[] modifiedContent = strategy.addServiceKeyToRequestBody(originalContent.getBytes());
        String modifiedContentStr = new String(modifiedContent);
        assertTrue(modifiedContentStr.contains("\"service_key\":\"test-key\""), "Service key should be added to the request body");
        if (expectedKey != null) {
            Map<String, Object> modifiedParsed = objectMapper.readValue(modifiedContent, Map.class);
            assertTrue(modifiedParsed.containsKey(expectedKey), "Modified content should still contain the expected key");
        }
    }
}
