package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
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

    private static JsonSchemaFilter categorySchema() {
        return JsonSchemaFilter.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of("category", JsonSchemaFilter.builder().type("string").build()))
            .build();
    }

    @Test
    void process_delegatesToBackend() {
        JsonSchemaFilter schema = categorySchema();

        GenMetadataProcessor processor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData) -> {
                TreeMap<String, Object> result = new TreeMap<>();
                result.put("category", "Excluded");
                return result;
            },
            OBJECT_MAPPER,
            4096);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) processor.process(
            "Classify the prompt", schema, "hello");
        assertEquals("Excluded", out.get("category"));
    }

    @Test
    void process_retriesOnSchemaMismatch() {
        JsonSchemaFilter schema = categorySchema();
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
            4096,
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
        JsonSchemaFilter schema = categorySchema();

        GenMetadataProcessor processor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData) -> """
                {"type":"object","properties":{"category":{"type":"string"}},"required":["category"]}
                """,
            OBJECT_MAPPER,
            4096,
            1,
            new JsonSchemaValidationUtils());

        assertThrows(GenMetadataAugmentException.class,
            () -> processor.process("Classify the prompt", schema, "hello"));
    }

    @Test
    void parseModelJson_extractsFromMarkdownFences() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER);
        Object out = processor.parseModelJson("""
            ```json
            {"category": "Email Drafting"}
            ```
            """);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) out;
        assertEquals("Email Drafting", map.get("category"));
    }

    @Test
    void parseModelJson_extractsJsonAfterProsePrefix() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER);
        Object out = processor.parseModelJson(
            "Here is the JSON requested:\n{\"category\":\"Excluded\"}");
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) out;
        assertEquals("Excluded", map.get("category"));
    }

    @Test
    void parseModelJson_recoversClassifyLabelFromTruncatedJson() {
        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of(
                "category", JsonSchemaFilter.builder()
                    .type("string")
                    .enumValues(List.of("Email Drafting", "Excluded", "Uncategorized"))
                    .build()))
            .build();
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER);
        Object out = processor.parseModelJson(
            "Here is the JSON requested:\n{\"category\": \"Email Drafting", schema);
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) out;
        assertEquals("Email Drafting", map.get("category"));
    }

    @Test
    void parseModelJson_parsesQuotedJsonStringForRootEnum() {
        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("string")
            .enumValues(List.of("Email Drafting", "Excluded", "Uncategorized"))
            .build();
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER);
        assertEquals("Email Drafting",
            processor.parseModelJson("\"Email Drafting\"", schema));
        assertEquals("Excluded",
            processor.parseModelJson("Excluded", schema));
    }

    @Test
    void process_throwsWhenPromptMissing() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER);
        assertThrows(GenMetadataAugmentException.class,
            () -> processor.process(null, JsonSchemaFilter.builder().type("object").build(), "x"));
    }

    @Test
    void serializeInput_truncatesNonStringValues() throws Exception {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 20);
        String serialized = processor.serializeInput(Map.of("text", "a".repeat(100)));
        assertTrue(serialized.length() <= 20);
    }

    @Test
    void serializeInput_mapPutsTitleBodyFirst_survivesTruncation() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 80);
        Map<String, Object> pr = new LinkedHashMap<>();
        pr.put("id", 42);
        pr.put("user", Map.of("login", "a".repeat(200)));
        pr.put("title", "Fix crash");
        pr.put("body", "Guards null");
        pr.put("+self:genMetadata", "should-not-appear");

        String serialized = processor.serializeInput(pr);

        assertTrue(serialized.length() <= 80);
        assertTrue(serialized.contains("Fix crash"), "truncated input should still include title");
        assertTrue(serialized.contains("\"title\""), serialized);
        // title/body ordered before large user blob
        assertTrue(serialized.indexOf("title") < serialized.indexOf("user")
                || !serialized.contains("user"),
            "title should appear before user (or user truncated away)");
        assertFalse(serialized.contains("+self:genMetadata"));
        assertFalse(serialized.contains("should-not-appear"));
    }

    @Test
    void serializeInput_mapIncludesBodyWhenBudgetAllows() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), OBJECT_MAPPER, 4096);
        Map<String, Object> pr = new LinkedHashMap<>();
        pr.put("user", Map.of("login", "alice", "bio", "x".repeat(100)));
        pr.put("title", "Add feature");
        pr.put("body", "Implements OAuth flow");

        String serialized = processor.serializeInput(pr);

        assertTrue(serialized.contains("Add feature"));
        assertTrue(serialized.contains("Implements OAuth flow"));
        assertTrue(serialized.indexOf("\"title\"") < serialized.indexOf("\"body\""));
        assertTrue(serialized.indexOf("\"body\"") < serialized.indexOf("\"user\""));
    }

    @Test
    void process_classify_acceptsJsonObjectInput_stringEnum() {
        JsonSchemaFilter schema = JsonSchemaFilter.builder()
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
            4096);

        Object out = processor.process("Classify", schema,
            Map.of("title", "Fix NPE", "body", "null guard"));
        assertEquals("Bugfix", out);
    }

    @Test
    void process_classify_acceptsJsonObjectInput_categoryObjectSchema() {
        JsonSchemaFilter schema = categorySchema();

        GenMetadataProcessor processor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData) -> {
                assertTrue(inputData.contains("\"body\""));
                return "{\"category\":\"Excluded\"}";
            },
            OBJECT_MAPPER,
            4096,
            2,
            new JsonSchemaValidationUtils());

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) processor.process(
            "Classify", schema, Map.of("title", "hi", "body", "thanks"));
        assertEquals("Excluded", out.get("category"));
    }
}
