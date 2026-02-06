package com.avaulta.gateway.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class EndpointTest {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final ObjectMapper jsonMapper = new ObjectMapper();

    @SneakyThrows
    @Test
    public void toYaml_withHeaders() {
        // Test serialization of Endpoint with allowedRequestHeaders
        final String EXPECTED = """
            ---
            pathTemplate: "/api/{id}/users"
            allowedMethods:
            - "GET"
            - "POST"
            allowedRequestHeaders:
            - "Authorization"
            - "X-Custom-Header"
            """;

        Endpoint endpoint = Endpoint.builder()
            .pathTemplate("/api/{id}/users")
            .allowedMethods(Set.of("GET", "POST"))
            .allowedRequestHeaders(Set.of("Authorization", "X-Custom-Header"))
            .build();

        String yaml = yamlMapper.writeValueAsString(endpoint);
        assertEquals(EXPECTED, yaml);
    }

    @SneakyThrows
    @Test
    public void fromYaml_withHeaders() {
        // Test deserialization of Endpoint with allowedRequestHeaders
        final String YAML = """
            ---
            pathTemplate: "/api/{id}/users"
            allowedMethods:
            - "GET"
            - "POST"
            allowedRequestHeaders:
            - "Authorization"
            - "X-Custom-Header"
            """;

        Endpoint endpoint = yamlMapper.readerFor(Endpoint.class).readValue(YAML);

        assertNotNull(endpoint);
        assertEquals("/api/{id}/users", endpoint.getPathTemplate());
        assertNotNull(endpoint.getAllowedRequestHeaders());
        assertEquals(2, endpoint.getAllowedRequestHeaders().size());
        assertTrue(endpoint.getAllowedRequestHeaders().contains("Authorization"));
        assertTrue(endpoint.getAllowedRequestHeaders().contains("X-Custom-Header"));
        assertEquals(2, endpoint.getAllowedMethods().size());
        assertTrue(endpoint.getAllowedMethods().contains("GET"));
        assertTrue(endpoint.getAllowedMethods().contains("POST"));
    }

    @SneakyThrows
    @Test
    public void toJson_withHeaders() {
        // Test JSON serialization of Endpoint with allowedRequestHeaders
        Endpoint endpoint = Endpoint.builder()
            .pathTemplate("/api/{id}/users")
            .allowedMethods(Set.of("GET", "POST"))
            .allowedRequestHeaders(Set.of("Authorization", "X-Custom-Header"))
            .build();

        String json = jsonMapper.writeValueAsString(endpoint);

        // Verify JSON contains expected fields
        assertTrue(json.contains("\"pathTemplate\""));
        assertTrue(json.contains("\"/api/{id}/users\""));
        assertTrue(json.contains("\"allowedMethods\""));
        assertTrue(json.contains("\"allowedRequestHeaders\""));
        assertTrue(json.contains("\"Authorization\""));
        assertTrue(json.contains("\"X-Custom-Header\""));
    }

    @SneakyThrows
    @Test
    public void fromJson_withHeaders() {
        // Test JSON deserialization of Endpoint with allowedRequestHeaders
        String json = "{\"pathTemplate\":\"/api/{id}/users\"," +
            "\"allowedMethods\":[\"GET\",\"POST\"]," +
            "\"allowedRequestHeaders\":[\"Authorization\",\"X-Custom-Header\"]}";

        Endpoint endpoint = jsonMapper.readerFor(Endpoint.class).readValue(json);

        assertNotNull(endpoint);
        assertEquals("/api/{id}/users", endpoint.getPathTemplate());
        assertNotNull(endpoint.getAllowedRequestHeaders());
        assertEquals(2, endpoint.getAllowedRequestHeaders().size());
        assertTrue(endpoint.getAllowedRequestHeaders().contains("Authorization"));
        assertTrue(endpoint.getAllowedRequestHeaders().contains("X-Custom-Header"));
    }

    @SneakyThrows
    @Test
    public void yamlRoundtrip_withAllFields() {
        // Test YAML roundtrip with multiple fields including headers
        Endpoint original = Endpoint.builder()
            .pathTemplate("/api/{version}/users/{id}")
            .allowedMethods(Set.of("GET", "POST", "PUT"))
            .allowedQueryParams(List.of("page", "limit", "sort"))
            .allowedRequestHeaders(Set.of("Authorization", "X-Custom-Header", "Accept"))
            .build();

        // Serialize to YAML
        String yaml = yamlMapper.writeValueAsString(original);

        // Deserialize back
        Endpoint deserialized = yamlMapper.readerFor(Endpoint.class).readValue(yaml);

        // Verify all fields match
        assertEquals(original.getPathTemplate(), deserialized.getPathTemplate());
        assertEquals(original.getAllowedMethods(), deserialized.getAllowedMethods());
        assertEquals(original.getAllowedQueryParams(), deserialized.getAllowedQueryParams());
        assertNotNull(deserialized.getAllowedRequestHeaders());
        assertEquals(original.getAllowedRequestHeaders(), deserialized.getAllowedRequestHeaders());
    }

    @SneakyThrows
    @Test
    public void jsonRoundtrip_withAllFields() {
        // Test JSON roundtrip with multiple fields including headers
        Endpoint original = Endpoint.builder()
            .pathTemplate("/api/{version}/users/{id}")
            .allowedMethods(Set.of("GET", "POST", "PUT"))
            .allowedQueryParams(List.of("page", "limit", "sort"))
            .allowedRequestHeaders(Set.of("Authorization", "X-Custom-Header", "Accept"))
            .build();

        // Serialize to JSON
        String json = jsonMapper.writeValueAsString(original);

        // Deserialize back
        Endpoint deserialized = jsonMapper.readerFor(Endpoint.class).readValue(json);

        // Verify all fields match
        assertEquals(original.getPathTemplate(), deserialized.getPathTemplate());
        assertEquals(original.getAllowedMethods(), deserialized.getAllowedMethods());
        assertEquals(original.getAllowedQueryParams(), deserialized.getAllowedQueryParams());
        assertNotNull(deserialized.getAllowedRequestHeaders());
        assertEquals(original.getAllowedRequestHeaders(), deserialized.getAllowedRequestHeaders());
    }

    @SneakyThrows
    @Test
    public void toYaml_headersNotIncludedWhenEmpty() {
        // Verify that empty headers are not included in YAML (due to @JsonInclude(NON_EMPTY))
        Endpoint endpoint = Endpoint.builder()
            .pathTemplate("/api/users")
            .build();

        String yaml = yamlMapper.writeValueAsString(endpoint);

        // Should not contain allowedRequestHeaders when not set
        assertFalse(yaml.contains("allowedRequestHeaders"));
    }

    @SneakyThrows
    @Test
    public void toYaml_withPathRegex() {
        // Test with pathRegex instead of pathTemplate
        final String EXPECTED = """
            ---
            pathRegex: "^/api/v[0-9]+/users/?.*$"
            allowedRequestHeaders:
            - "Authorization"
            """;

        Endpoint endpoint = Endpoint.builder()
            .pathRegex("^/api/v[0-9]+/users/?.*$")
            .allowedRequestHeaders(Set.of("Authorization"))
            .build();

        String yaml = yamlMapper.writeValueAsString(endpoint);
        assertEquals(EXPECTED, yaml);
    }

    @SneakyThrows
    @Test
    public void toYaml_complexEndpoint() {
        // Test a more complex endpoint with multiple features
        Endpoint endpoint = Endpoint.builder()
            .pathTemplate("/api/{version}/users/{id}")
            .allowedMethods(Set.of("DELETE", "GET", "PATCH", "POST", "PUT"))
            .allowedQueryParams(List.of("fields", "include", "page"))
            .allowedRequestHeaders(Set.of("Accept", "Authorization", "Content-Type", "X-Request-ID"))
            .build();

        String yaml = yamlMapper.writeValueAsString(endpoint);

        // Deserialize and verify
        Endpoint deserialized = yamlMapper.readerFor(Endpoint.class).readValue(yaml);

        assertEquals(endpoint.getPathTemplate(), deserialized.getPathTemplate());
        assertEquals(endpoint.getAllowedMethods(), deserialized.getAllowedMethods());
        assertEquals(endpoint.getAllowedQueryParams(), deserialized.getAllowedQueryParams());
        assertEquals(endpoint.getAllowedRequestHeaders(), deserialized.getAllowedRequestHeaders());
    }
}
