package com.avaulta.gateway.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSchemaValidationUtilsTest {

    private JsonSchemaValidationUtils validationUtils;
    private com.avaulta.gateway.rules.JsonSchema testSchema;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        validationUtils = new JsonSchemaValidationUtils(objectMapper);

        // Create the test schema with representative subset
        testSchema = createTestSchema();
    }

    private com.avaulta.gateway.rules.JsonSchema createTestSchema() {
        Map<String, com.avaulta.gateway.rules.JsonSchema> properties = new HashMap<>();

        // startDate - number, optional
        properties.put("startDate", com.avaulta.gateway.rules.JsonSchema.builder()
                .type("number")
                .build());

        // email - string, optional
        properties.put("email", com.avaulta.gateway.rules.JsonSchema.builder()
                .type("string")
                .build());

        return com.avaulta.gateway.rules.JsonSchema.builder()
                .type("object")
                .properties(properties)
                .additionalProperties(false) // Explicitly disallow extra fields
                .build();
    }

    @Test
    void testValidateJsonBySchema_ValidEmptyObject() {
        String jsonString = "{}";
        assertTrue(validationUtils.validateJsonBySchema(jsonString, testSchema));
    }

    @Test
    void testValidateJsonBySchema_ValidWithAllFields() {
        String jsonString = """
                {
                    "startDate": 1640995200000,
                    "email": "test@example.com"
                }
                """;
        assertTrue(validationUtils.validateJsonBySchema(jsonString, testSchema));
    }

    @Test
    void testValidateJsonBySchema_ValidWithNumberField() {
        String jsonString = """
                {
                    "startDate": 1640995200000
                }
                """;
        assertTrue(validationUtils.validateJsonBySchema(jsonString, testSchema));
    }

    @Test
    void testValidateJsonBySchema_ValidWithStringField() {
        String jsonString = """
                {
                    "email": "test@example.com"
                }
                """;
        assertTrue(validationUtils.validateJsonBySchema(jsonString, testSchema));
    }

    @Test
    void testValidateJsonBySchema_InvalidNotObject() {
        String jsonString = "[]";
        assertFalse(validationUtils.validateJsonBySchema(jsonString, testSchema));
    }

    @Test
    void testValidateJsonBySchema_InvalidExtraField() {
        String jsonString = """
                {
                    "startDate": 1640995200000,
                    "extraField": "should not be allowed"
                }
                """;
        assertFalse(validationUtils.validateJsonBySchema(jsonString, testSchema));
    }

    @Test
    void testValidateJsonBySchema_InvalidWrongTypeForNumber() {
        String jsonString = """
                {
                    "startDate": "not a number"
                }
                """;
        assertFalse(validationUtils.validateJsonBySchema(jsonString, testSchema));
    }

    @Test
    void testValidateJsonBySchema_InvalidWrongTypeForString() {
        String jsonString = """
                {
                    "email": 12345
                }
                """;
        assertFalse(validationUtils.validateJsonBySchema(jsonString, testSchema));
    }

    @ParameterizedTest
    @CsvSource({
            "p~asdfasdfasdfasdfasdfasdfasdfsadfasdfasdfasdfasdf, true",
            "alice@acme.com, false"
    })
    void testPseudonymFormat(String value, boolean expected) {
        testSchema = com.avaulta.gateway.rules.JsonSchema.builder()
                .type("string").format("pseudonym").build();
        String jsonString = "\"" + value + "\"";
        assertEquals(expected, validationUtils.validateJsonBySchema(jsonString, testSchema));
    }

    @ParameterizedTest
    @CsvSource({
            "p~asdfasdfasdfasdfasdfasdfasdfsadfasdfasdfasdfasdf, true",
            "alice@acme.com, false"
    })
    void testPseudonymPattern(String value, boolean expected) {
        testSchema = com.avaulta.gateway.rules.JsonSchema.builder()
                .type("string").pattern("^p~[a-zA-Z0-9_-]{43}$").build();
        String jsonString = "\"" + value + "\"";
        assertEquals(expected, validationUtils.validateJsonBySchema(jsonString, testSchema));
    }
}
