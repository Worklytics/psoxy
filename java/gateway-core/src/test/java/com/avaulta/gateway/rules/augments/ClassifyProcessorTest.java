package com.avaulta.gateway.rules.augments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassifyProcessorTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> CLASSES =
        List.of("Email Drafting", "Research and Ideation", "Uncategorized", "Excluded");

    @Test
    void compute_returnsExactClass() {
        ClassifyProcessor processor = processorReturning("Research and Ideation");
        Object out = processor.compute(classifyAugment(), "write an email");
        assertEquals("Research and Ideation", out);
    }

    @Test
    void compute_usesSubstringInProse() {
        ClassifyProcessor processor = processorReturning(
            "Here is the label: Email Drafting (done)");
        assertEquals("Email Drafting", processor.compute(classifyAugment(), "x"));
    }

    @Test
    void compute_usesClassInsideQuotedJson() {
        ClassifyProcessor processor = processorReturning("\"Research and Ideation\"");
        assertEquals("Research and Ideation", processor.compute(classifyAugment(), "x"));
    }

    @Test
    void compute_prefersLongerClassWhenBothMatch() {
        ClassifyProcessor processor = processorReturning("Email Drafting vs Email");
        assertEquals("Email Drafting",
            processor.compute(Augment.Classify.builder()
                .jsonPath("$.x")
                .prompt("classify")
                .classes(List.of("Email", "Email Drafting"))
                .build(), "x"));
    }

    @Test
    void findClass_isCaseSensitive() {
        ClassifyProcessor processor = processorReturning("ignored");
        assertNull(processor.findClass("excluded", List.of("Excluded")));
        assertEquals("Excluded", processor.findClass("say Excluded please", List.of("Excluded")));
    }

    @Test
    void findClass_doesNotMatchLetterInsideAnotherWord() {
        ClassifyProcessor processor = processorReturning("ignored");
        assertEquals("B", processor.findClass("The Answer is B", List.of("A", "B")));
        assertNull(processor.findClass("The Answer is unknown", List.of("A", "B")));
    }

    @Test
    void findClass_doesNotMatchShorterLabelInsideLongerWord() {
        ClassifyProcessor processor = processorReturning("ignored");
        assertEquals("dog", processor.findClass("category vs dog", List.of("cat", "dog")));
        assertNull(processor.findClass("category", List.of("cat")));
    }

    @Test
    void findClass_trimsConfiguredClassNames() {
        ClassifyProcessor processor = processorReturning("ignored");
        assertEquals("Feature", processor.findClass("Feature", List.of(" Feature ")));
    }

    @Test
    void compute_throwsWhenNoClassPresent() {
        ClassifyProcessor processor = processorReturning("not a known label");
        assertThrows(GenMetadataAugmentException.class,
            () -> processor.compute(classifyAugment(), "x"));
    }

    @Test
    void compute_serializesMapInput() {
        ClassifyProcessor processor = new ClassifyProcessor(
            (taskPrompt, outputSchema, inputData, options) -> {
                assertTrue(inputData.contains("\"title\""));
                assertTrue(inputData.contains("Fix NPE"));
                return GenMetadataInferenceResult.of("Bugfix");
            },
            OBJECT_MAPPER,
            2);
        Object out = processor.compute(
            Augment.Classify.builder()
                .jsonPath("$")
                .prompt("Classify")
                .classes(List.of("Feature", "Bugfix", "Uncategorized"))
                .build(),
            Map.of("title", "Fix NPE", "body", "null guard"));
        assertEquals("Bugfix", out);
    }

    @Test
    void maxOutputTokens_isLongestClassLength() {
        Augment.Classify augment = Augment.Classify.builder()
            .jsonPath("$.x")
            .prompt("c")
            .classes(List.of("A", "Uncategorized", "Hi"))
            .build();
        assertEquals("Uncategorized".length(), augment.getMaxOutputTokens());
    }

    @Test
    void yaml_deserializesClassifyTag() throws Exception {
        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        String yaml = """
            !<classify>
            jsonPaths:
              - "$[*]"
            prompt: Classify this pull request.
            classes:
              - Feature
              - Bugfix
              - Uncategorized
            """;
        Augment augment = yamlMapper.readValue(yaml, Augment.class);
        assertTrue(augment instanceof Augment.Classify);
        Augment.Classify classify = (Augment.Classify) augment;
        assertEquals("Classify this pull request.", classify.getPrompt());
        assertEquals(List.of("Feature", "Bugfix", "Uncategorized"), classify.getClasses());
        assertEquals("classify", classify.getFunctionName());
        assertEquals("Uncategorized".length(), classify.getMaxOutputTokens());
        assertNull(classify.getOutputSchema());
    }

    private static Augment.Classify classifyAugment() {
        return Augment.Classify.builder()
            .jsonPath("$.content")
            .prompt("classify")
            .classes(CLASSES)
            .build();
    }

    private static ClassifyProcessor processorReturning(String raw) {
        return new ClassifyProcessor(
            (taskPrompt, outputSchema, inputData, options) -> GenMetadataInferenceResult.of(raw),
            OBJECT_MAPPER,
            1);
    }
}
