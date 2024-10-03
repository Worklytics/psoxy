package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.impl.GoogleCloudPlatformServiceAccountKeyAuthStrategy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckResultTest {

    ObjectMapper objectMapper = new ObjectMapper();


    @Test
    public void json() throws JsonProcessingException {
        final String JSON = "{\n" +
            "  \"bundleFilename\" : \"psoxy-aws-rc-v0.1.15.jar\",\n" +
            "  \"callerIp\" : null,\n" +
            "  \"configPropertiesLastModified\" : null,\n" +
            "  \"configuredHost\" : \"blah.com\",\n" +
            "  \"configuredSource\" : \"blah\",\n" +
            "  \"javaSourceCodeVersion\" : \"v0.EXAMPLE\",\n" +
            "  \"missingConfigProperties\" : [ \"SERVICE_ACCOUNT_KEY\" ],\n" +
            "  \"nonDefaultSalt\" : true,\n" +
            "  \"pseudonymImplementation\" : null,\n" +
            "  \"pseudonymizeAppIds\" : null,\n" +
            "  \"sourceAuthGrantType\" : null,\n" +
            "  \"sourceAuthStrategy\" : null,\n" +
            "  \"version\" : \"rc-v0.1.15\"\n" +
            "}";


        HealthCheckResult healthCheckResult = HealthCheckResult.builder()
            .javaSourceCodeVersion("v0.EXAMPLE") //avoid needing to change this test every version change
            .bundleFilename("psoxy-aws-rc-v0.1.15.jar")
            .configuredSource("blah")
            .configuredHost("blah.com")
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

    @SneakyThrows
    @ValueSource(strings={
        "{\n" +
            "              \"bundleFilename\" : \"psoxy-aws-rc-v0.1.15.jar\",\n" +
            "              \"callerIp\" : null,\n" +
            "              \"configPropertiesLastModified\" : null,\n" +
            "              \"configuredHost\" : \"blah.com\",\n" +
            "              \"configuredSource\" : \"blah\",\n" +
            "              \"version\" : \"v0.EXAMPLE\",\n" +
            "              \"missingConfigProperties\" : [ \"SERVICE_ACCOUNT_KEY\" ],\n" +
            "              \"nonDefaultSalt\" : true,\n" +
            "              \"pseudonymImplementation\" : null,\n" +
            "              \"sourceAuthGrantType\" : null,\n" +
            "              \"sourceAuthStrategy\" : null,\n" +
            "              \"version\" : \"rc-v0.1.15\"\n" +
            "            }"

    })
    @ParameterizedTest
    public void legacyJson(String legacyJson) {
        // just make sure it doesn't blow up
        objectMapper.readerFor(HealthCheckResult.class).readValue(legacyJson);
    }

}
