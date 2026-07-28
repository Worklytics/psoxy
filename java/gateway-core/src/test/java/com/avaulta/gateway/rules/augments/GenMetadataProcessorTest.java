package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            new JsonSchemaValidationUtils(OBJECT_MAPPER));

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
            new JsonSchemaValidationUtils(OBJECT_MAPPER));

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
}
