package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchema;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenMetadataProcessorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static JsonSchema categorySchema() {
        return JsonSchema.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of("category", JsonSchema.builder().type("string").build()))
            .build();
    }

    @Test
    void process_delegatesToBackend() {
        JsonSchema schema = categorySchema();

        GenMetadataProcessor processor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData) -> {
                TreeMap<String, Object> result = new TreeMap<>();
                result.put("category", "Excluded");
                return result;
            },
            OBJECT_MAPPER,
            2,
            new JsonSchemaValidationUtils());

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) processor.process(
            "Classify the prompt", schema, "hello");
        assertEquals("Excluded", out.get("category"));
    }

    @Test
    void process_retriesOnSchemaMismatch() {
        JsonSchema schema = categorySchema();
        AtomicInteger calls = new AtomicInteger();

        GenMetadataProcessor processor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData) -> {
                if (calls.incrementAndGet() == 1) {
                    return """
                        {"type":"object","properties":{"category":{"type":"string"}},"required":["category"]}
                        """;
                }
                return "{\"category\":\"Excluded\"}";
            },
            OBJECT_MAPPER,
            2,
            new JsonSchemaValidationUtils());

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) processor.process(
            "Classify the prompt", schema, "hello");
        assertEquals("Excluded", out.get("category"));
        assertEquals(2, calls.get());
    }

    @Test
    void process_failsAfterExhaustingRetries() {
        JsonSchema schema = categorySchema();

        GenMetadataProcessor processor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData) -> """
                {"type":"object","properties":{"category":{"type":"string"}},"required":["category"]}
                """,
            OBJECT_MAPPER,
            1,
            new JsonSchemaValidationUtils());

        assertThrows(GenMetadataAugmentException.class,
            () -> processor.process("Classify the prompt", schema, "hello"));
    }

    @Test
    void parseModelJson_extractsFromMarkdownFences() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 2, new JsonSchemaValidationUtils());
        Object out = processor.parseModelJson("""
            ```json
            {"category": "Email Drafting"}
            ```
            """, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) out;
        assertEquals("Email Drafting", map.get("category"));
    }

    @Test
    void parseModelJson_extractsJsonAfterProsePrefix() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 2, new JsonSchemaValidationUtils());
        Object out = processor.parseModelJson(
            "Here is the JSON requested:\n{\"category\":\"Excluded\"}", null);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) out;
        assertEquals("Excluded", map.get("category"));
    }

    @Test
    void parseModelJson_recoversClassifyLabelFromTruncatedJson() {
        JsonSchema schema = JsonSchema.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of(
                "category", JsonSchema.builder()
                    .type("string")
                    .enumValues(List.of("Email Drafting", "Excluded", "Uncategorized"))
                    .build()))
            .build();
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 2, new JsonSchemaValidationUtils());
        Object out = processor.parseModelJson(
            "Here is the JSON requested:\n{\"category\": \"Email Drafting", schema);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) out;
        assertEquals("Email Drafting", map.get("category"));
    }

    @Test
    void parseModelJson_parsesQuotedJsonStringForRootEnum() {
        JsonSchema schema = JsonSchema.builder()
            .type("string")
            .enumValues(List.of("Email Drafting", "Excluded", "Uncategorized"))
            .build();
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 2, new JsonSchemaValidationUtils());
        assertEquals("Email Drafting",
            processor.parseModelJson("\"Email Drafting\"", schema));
        assertEquals("Excluded",
            processor.parseModelJson("Excluded", schema));
    }

    @Test
    void parseModelJson_parsesJsonArrayRoot() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 2, new JsonSchemaValidationUtils());
        Object out = processor.parseModelJson(
            "Here is the JSON requested:\n[{\"label\":\"a\"},{\"label\":\"b\"}]", null);
        assertTrue(out instanceof List<?>);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) out;
        assertEquals(2, list.size());
        assertEquals("a", list.get(0).get("label"));
    }

    @Test
    void process_throwsWhenPromptMissing() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 2, new JsonSchemaValidationUtils());
        assertThrows(GenMetadataAugmentException.class,
            () -> processor.process(null, JsonSchema.builder().type("object").build(), "x"));
    }

    @Test
    void serializeInput_mapPutsTitleBodyFirst() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 2, new JsonSchemaValidationUtils());
        Map<String, Object> pr = new LinkedHashMap<>();
        pr.put("id", 42);
        pr.put("user", Map.of("login", "a".repeat(200)));
        pr.put("title", "Fix crash");
        pr.put("body", "Guards null");
        pr.put("+self:genMetadata", "should-not-appear");

        String serialized = processor.serializeInput(pr);

        assertTrue(serialized.contains("Fix crash"));
        assertTrue(serialized.contains("\"title\""), serialized);
        assertTrue(serialized.contains("+self:genMetadata"));
        assertTrue(serialized.contains("should-not-appear"));
    }

    @Test
    void serializeInput_mapIncludesBodyWhenBudgetAllows() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 2, new JsonSchemaValidationUtils());
        Map<String, Object> pr = new LinkedHashMap<>();
        pr.put("user", Map.of("login", "alice", "bio", "x".repeat(100)));
        pr.put("title", "Add feature");
        pr.put("body", "Implements OAuth flow");

        String serialized = processor.serializeInput(pr);

        assertTrue(serialized.contains("Add feature"));
        assertTrue(serialized.contains("Implements OAuth flow"));
    }

    @Test
    void process_classify_acceptsJsonObjectInput_stringEnum() {
        JsonSchema schema = JsonSchema.builder()
            .type("string")
            .enumValues(List.of("Feature", "Bugfix", "Uncategorized"))
            .build();

        GenMetadataProcessor processor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData) -> {
                assertTrue(inputData.contains("\"title\""));
                assertTrue(inputData.contains("Fix NPE"));
                return "Bugfix";
            },
            OBJECT_MAPPER,
            2,
            new JsonSchemaValidationUtils());

        Object out = processor.process("Classify", schema,
            Map.of("title", "Fix NPE", "body", "null guard"));
        assertEquals("Bugfix", out);
    }

    @Test
    void process_classify_acceptsJsonObjectInput_categoryObjectSchema() {
        JsonSchema schema = categorySchema();

        GenMetadataProcessor processor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData) -> {
                assertTrue(inputData.contains("\"body\""));
                return "{\"category\":\"Excluded\"}";
            },
            OBJECT_MAPPER,
            2,
            new JsonSchemaValidationUtils());

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) processor.process(
            "Classify", schema, Map.of("title", "hi", "body", "thanks"));
        assertEquals("Excluded", out.get("category"));
    }
}
