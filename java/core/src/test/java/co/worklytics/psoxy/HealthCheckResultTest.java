package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.impl.GoogleCloudPlatformServiceAccountKeyAuthStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckResultTest {



    @Test
    public void json() throws JsonProcessingException {
        final String JSON = "{\n" +
            "  \"bundleFilename\" : null,\n" +
            "  \"configPropertiesLastModified\" : null,\n" +
            "  \"configuredSource\" : \"blah\",\n" +
            "  \"missingConfigProperties\" : [ \"SERVICE_ACCOUNT_KEY\" ],\n" +
            "  \"nonDefaultSalt\" : true,\n" +
            "  \"pseudonymImplementation\" : null,\n" +
            "  \"sourceAuthGrantType\" : null,\n" +
            "  \"sourceAuthStrategy\" : null,\n" +
            "  \"version\" : \"v0.EXAMPLE\"\n" +
            "}";

        ObjectMapper objectMapper = new ObjectMapper();

        HealthCheckResult healthCheckResult = HealthCheckResult.builder()
            .version("v0.EXAMPLE") //avoid needing to change this test every version change
            .configuredSource("blah")
            .nonDefaultSalt(true)
            .missingConfigProperties(Collections.singleton(GoogleCloudPlatformServiceAccountKeyAuthStrategy.ConfigProperty.SERVICE_ACCOUNT_KEY.name()))
            .build();

        assertEquals(JSON, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(healthCheckResult));

        HealthCheckResult fromJson = objectMapper.readerFor(HealthCheckResult.class).readValue(JSON);
        assertEquals("blah", fromJson.getConfiguredSource());
    }

    @Test
    public void versionFromBundleFilename() {


        HealthCheckResult healthCheckResult = HealthCheckResult.builder()
            .bundleFilename("psoxy-aws-rc-v0.1.15.jar")
            .build();

        assertEquals("rc-v0.1.15", healthCheckResult.getVersion());

        healthCheckResult = HealthCheckResult.builder()
            .bundleFilename("psoxy-aws-v0.1.15.jar")
            .build();

        assertEquals("v0.1.15", healthCheckResult.getVersion());

        healthCheckResult = HealthCheckResult.builder()
            .bundleFilename("psoxy-gcp-v0.1.15.jar")
            .build();

        assertEquals("v0.1.15", healthCheckResult.getVersion());
    }

}
