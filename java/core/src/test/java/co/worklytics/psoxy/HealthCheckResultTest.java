package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.SourceAuthConfigProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckResultTest {



    @Test
    public void json() throws JsonProcessingException {
        final String JSON = "{\"configuredSource\":\"blah\",\"missingConfigProperties\":[\"SERVICE_ACCOUNT_KEY\"],\"nonDefaultSalt\":true}";

        ObjectMapper objectMapper = new ObjectMapper();

        HealthCheckResult healthCheckResult = HealthCheckResult.builder()
            .configuredSource("blah")
            .nonDefaultSalt(true)
            .missingConfigProperties(Collections.singleton(SourceAuthConfigProperty.SERVICE_ACCOUNT_KEY.name()))
            .build();

        assertEquals(JSON, objectMapper.writeValueAsString(healthCheckResult));

        HealthCheckResult fromJson = objectMapper.readerFor(HealthCheckResult.class).readValue(JSON);
        assertEquals("blah", fromJson.getConfiguredSource());
    }

}
