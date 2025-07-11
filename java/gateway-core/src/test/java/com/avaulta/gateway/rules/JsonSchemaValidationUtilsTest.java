package com.avaulta.gateway.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @ParameterizedTest
    @CsvSource({
            "'', true",
            "startDate=1640995200000, true",
            "email=test@example.com, true",
            "startDate=1640995200000&email=test@example.com, true",
            "userId=p~asdfasdfasdfasdfasdfasdfasdfsadfasdfasdfasdfasdf&name=John, true",
            "startDate=notANumber, false",
            "startDate=1640995200000&extraField=shouldNotBeAllowed, false",
            "userId=invalid-pseudonym&name=John, false",
            "unknownField=value, false"
    })
    void testValidateFormUrlEncodedBySchema_StrictValidation(String formBody, boolean expected) {
        com.avaulta.gateway.rules.JsonSchema formSchema =
                com.avaulta.gateway.rules.JsonSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "startDate", com.avaulta.gateway.rules.JsonSchema.builder()
                                        .type("string")
                                        .pattern("^[0-9]+$") // the soln for numbers in
                                                             // x-www-form-urlencoded case
                                        .build(),
                                "email", com.avaulta.gateway.rules.JsonSchema.builder()
                                        .type("string")
                                        .build(),
                                "userId", com.avaulta.gateway.rules.JsonSchema.builder()
                                        .type("string")
                                        .format("pseudonym")
                                        .build(),
                                "name", com.avaulta.gateway.rules.JsonSchema.builder()
                                        .type("string")
                                        .build()))
                        .additionalProperties(false)
                        .build();

        assertEquals(expected,
                validationUtils.validateFormUrlEncodedBySchema(formBody, formSchema));
    }

    @ParameterizedTest
    @CsvSource({
            "'', true",
            "startDate=1640995200000, true",
            "email=test@example.com, true",
            "startDate=1640995200000&email=test@example.com, true",
            "startDate=1640995200000&extraField=allowed, true",
            "unknownField=value, true"
    })
    void testValidateFormUrlEncodedBySchema_AdditionalPropertiesTrue(String formBody,
            boolean expected) {
        com.avaulta.gateway.rules.JsonSchema formSchema =
                com.avaulta.gateway.rules.JsonSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "startDate", com.avaulta.gateway.rules.JsonSchema.builder()
                                        .type("string")
                                        .build(),
                                "email", com.avaulta.gateway.rules.JsonSchema.builder()
                                        .type("string")
                                        .build()))
                        .additionalProperties(true)
                        .build();

        assertEquals(expected,
                validationUtils.validateFormUrlEncodedBySchema(formBody, formSchema));
    }

    @ParameterizedTest
    @CsvSource({
            "'', true",
            "startDate=1640995200000, true",
            "email=test@example.com, true",
            "startDate=1640995200000&email=test@example.com, true",
            "startDate=1640995200000&extraField=allowed, true",
            "unknownField=value, true"
    })
    void testValidateFormUrlEncodedBySchema_AdditionalPropertiesNotSpecified(String formBody,
            boolean expected) {
        com.avaulta.gateway.rules.JsonSchema formSchema =
                com.avaulta.gateway.rules.JsonSchema.builder()
                        .type("object")
                        .properties(Map.of(
                                "startDate", com.avaulta.gateway.rules.JsonSchema.builder()
                                        .type("string")
                                        .build(),
                                "email", com.avaulta.gateway.rules.JsonSchema.builder()
                                        .type("string")
                                        .build()))
                        .build();

        assertEquals(expected,
                validationUtils.validateFormUrlEncodedBySchema(formBody, formSchema));
    }

    @Test
    void testValidateFormUrlEncodedBySchema_InvalidNonObjectSchema() {
        com.avaulta.gateway.rules.JsonSchema nonObjectSchema =
                com.avaulta.gateway.rules.JsonSchema.builder()
                        .type("string")
                        .build();

        String formBody = "test=value";

        assertThrows(IllegalArgumentException.class, () -> {
            validationUtils.validateFormUrlEncodedBySchema(formBody, nonObjectSchema);
        });
    }
}
