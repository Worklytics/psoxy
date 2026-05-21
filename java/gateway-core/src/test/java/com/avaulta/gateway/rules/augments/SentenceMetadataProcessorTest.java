package com.avaulta.gateway.rules.augments;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.avaulta.gateway.resources.BinaryResourceProvider;

import static org.junit.jupiter.api.Assertions.*;

class SentenceMetadataProcessorTest {

    @AfterEach
    void tearDown() {
        SentenceMetadataProcessor.resetForTests();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessWithModels() {
        // Skip test if models are not downloaded yet
        Assumptions.assumeTrue(
                SentenceMetadataProcessorTest.class.getResourceAsStream("/opennlp/en-sent.bin") != null,
                "OpenNLP models not present in classpath. Run tools/fetch-opennlp-models.sh first."
        );

        Map<String, List<String>> taxonomy = new TreeMap<>();
        taxonomy.put("CODE_ARTIFACT", List.of("code", "script", "function", "api"));
        taxonomy.put("MEDIUM", List.of("email", "message"));

        String text = "Please write a python script! Could you avoid sending an email?";

        Map<String, Object> result = SentenceMetadataProcessor.process(text, taxonomy);

        assertNotNull(result);
        assertFalse(result.isEmpty(), "Result should not be empty when models are loaded");
        assertTrue(result.containsKey("sentences"));
        assertTrue(result.containsKey("doc_summary"));

        List<Map<String, Object>> sentences = (List<Map<String, Object>>) result.get("sentences");
        assertEquals(2, sentences.size());

        // Second sentence: "Could you avoid sending an email?"
        Map<String, Object> s2 = sentences.get(1);
        assertEquals("interrogative", s2.get("type"), "Should detect question mark");

        Map<String, Object> signals2 = (Map<String, Object>) s2.get("signals");
        assertTrue((Boolean) signals2.get("question"), "Should be flagged as a question");
        assertTrue((Boolean) signals2.get("constraint"), "Should detect 'avoid' as constraint");

        List<Map<String, Object>> nouns2 = (List<Map<String, Object>>) s2.get("nouns");
        boolean foundMedium = nouns2.stream()
                .anyMatch(n -> "MEDIUM".equals(n.get("category")) && "email".equals(n.get("noun")));
        assertTrue(foundMedium, "Should detect 'email' as MEDIUM");

        // Doc summary checks
        Map<String, Object> docSummary = (Map<String, Object>) result.get("doc_summary");
        assertEquals(2, docSummary.get("sentence_count"));
        assertTrue((Boolean) docSummary.get("any_question"));
        assertTrue((Boolean) docSummary.get("any_constraint"));

        List<String> docCategories = (List<String>) docSummary.get("noun_categories");
        assertTrue(docCategories.contains("MEDIUM"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testProcessWithResourceProvider() {
        byte[] stubModel = "stub-model".getBytes(StandardCharsets.UTF_8);
        SentenceMetadataProcessor.configureResourceProvider(relativePath -> {
            if ("opennlp/en-sent.bin".equals(relativePath)
                    || "opennlp/en-pos-maxent.bin".equals(relativePath)
                    || "opennlp/en-chunker.bin".equals(relativePath)) {
                return Optional.of(new ByteArrayInputStream(stubModel));
            }
            return Optional.empty();
        });

        // Invalid model bytes should fail gracefully and return null
        assertNull(SentenceMetadataProcessor.process("Hello world.", Map.of()));
    }

    @Test
    void testEmptyText() {
        Assumptions.assumeTrue(
                SentenceMetadataProcessorTest.class.getResourceAsStream("/opennlp/en-sent.bin") != null,
                "OpenNLP models not present in classpath. Run tools/fetch-opennlp-models.sh first."
        );

        Map<String, Object> result = SentenceMetadataProcessor.process("", Map.of());
        assertNotNull(result);
        assertEquals(0, ((Map<?, ?>) result.get("doc_summary")).get("sentence_count"));
    }

    @Test
    void testWithoutModelsReturnsNull() {
        SentenceMetadataProcessor.configureResourceProvider(relativePath -> Optional.empty());
        assertNull(SentenceMetadataProcessor.process("Hello world.", Map.of()));
    }
}
