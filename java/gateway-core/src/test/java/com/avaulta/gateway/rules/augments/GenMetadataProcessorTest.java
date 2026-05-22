package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenMetadataProcessorTest {

    @AfterEach
    void tearDown() {
        GenMetadataProcessor.resetForTests();
    }

    @Test
    void process_delegatesToBackend() {
        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("object")
            .properties(Map.of("category", JsonSchemaFilter.builder().type("string").build()))
            .build();

        GenMetadataProcessor.configure((taskPrompt, outputSchema, inputData) -> {
            TreeMap<String, Object> result = new TreeMap<>();
            result.put("category", "Excluded");
            return result;
        }, new ObjectMapper(), 4096);

        @SuppressWarnings("unchecked")
        Map<String, Object> out = (Map<String, Object>) GenMetadataProcessor.process(
            "Classify the prompt", schema, "hello");
        assertEquals("Excluded", out.get("category"));
    }

    @Test
    void parseModelJson_extractsFromMarkdownFences() {
        Object out = GenMetadataProcessor.parseModelJson("""
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
        assertThrows(GenMetadataAugmentException.class,
            () -> GenMetadataProcessor.process(null, JsonSchemaFilter.builder().type("object").build(),
                "x"));
    }
}
