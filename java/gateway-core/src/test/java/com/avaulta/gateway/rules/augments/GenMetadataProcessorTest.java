package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenMetadataProcessorTest {

    @Test
    void process_delegatesToBackend() {
        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("object")
            .properties(Map.of("category", JsonSchemaFilter.builder().type("string").build()))
            .build();

        GenMetadataProcessor processor = new GenMetadataProcessor(
            (taskPrompt, outputSchema, inputData) -> {
                TreeMap<String, Object> result = new TreeMap<>();
                result.put("category", "Excluded");
                return result;
            },
            new ObjectMapper(),
            4096);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) processor.process(
            "Classify the prompt", schema, "hello");
        assertEquals("Excluded", out.get("category"));
    }

    @Test
    void parseModelJson_extractsFromMarkdownFences() {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), new ObjectMapper());
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
            new UnavailableGenMetadataBackend(), new ObjectMapper());
        assertThrows(GenMetadataAugmentException.class,
            () -> processor.process(null, JsonSchemaFilter.builder().type("object").build(), "x"));
    }

    @Test
    void serializeInput_truncatesNonStringValues() throws Exception {
        GenMetadataProcessor processor = new GenMetadataProcessor(
            new UnavailableGenMetadataBackend(), new ObjectMapper(), 20);
        String serialized = processor.serializeInput(Map.of("text", "a".repeat(100)));
        assertTrue(serialized.length() <= 20);
    }
}
