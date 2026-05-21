package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.resources.ResourceService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class SentenceMetadataProcessorTest {

    private static final Set<String> DEFAULT_HEDGE = Set.copyOf(Augment.SentenceMetadata.DEFAULT_HEDGE_WORDS);
    private static final Set<String> DEFAULT_CONSTRAINT = Set.copyOf(Augment.SentenceMetadata.DEFAULT_CONSTRAINT_WORDS);

    @AfterEach
    void tearDown() {
        SentenceMetadataProcessor.resetForTests();
    }

    @Test
    void testProcessWithModels() {
        Assumptions.assumeTrue(
                SentenceMetadataProcessorTest.class.getResourceAsStream("/opennlp/en-sent.bin") != null,
                "OpenNLP models not present in classpath. Run tools/fetch-opennlp-models.sh first."
        );

        configureClasspathResourceService();

        Map<String, List<String>> taxonomy = new TreeMap<>();
        taxonomy.put("CODE_ARTIFACT", List.of("code", "script", "function", "api"));
        taxonomy.put("MEDIUM", List.of("email", "message"));

        String text = "Please write a python script! Could you avoid sending an email?";

        SentenceMetadataResult result = SentenceMetadataProcessor.process(
            text, taxonomy, DEFAULT_HEDGE, DEFAULT_CONSTRAINT);

        assertNotNull(result);
        assertEquals(2, result.getSentences().size());

        SentenceMetadataResult.Sentence s2 = result.getSentences().get(1);
        assertEquals("interrogative", s2.getType());
        assertTrue(s2.getSignals().isQuestion());
        assertTrue(s2.getSignals().isConstraint());

        boolean foundMedium = s2.getNouns().stream()
            .anyMatch(n -> "MEDIUM".equals(n.getCategory()) && "email".equals(n.getNoun()));
        assertTrue(foundMedium);

        SentenceMetadataResult.DocSummary docSummary = result.getDocSummary();
        assertEquals(2, docSummary.getSentenceCount());
        assertTrue(docSummary.isAnyQuestion());
        assertTrue(docSummary.isAnyConstraint());
        assertTrue(docSummary.getNounCategories().contains("MEDIUM"));
    }

    @Test
    void testProcessWithInvalidModelBytes() {
        byte[] stubModel = "stub-model".getBytes(StandardCharsets.UTF_8);
        SentenceMetadataProcessor.configureResourceService(path -> {
            if (path.startsWith("opennlp/")) {
                return Optional.of(new ByteArrayInputStream(stubModel));
            }
            return Optional.empty();
        });

        assertNull(SentenceMetadataProcessor.process(
            "Hello world.", Map.of(), DEFAULT_HEDGE, DEFAULT_CONSTRAINT));
    }

    @Test
    void testEmptyText() {
        Assumptions.assumeTrue(
                SentenceMetadataProcessorTest.class.getResourceAsStream("/opennlp/en-sent.bin") != null,
                "OpenNLP models not present in classpath. Run tools/fetch-opennlp-models.sh first."
        );

        configureClasspathResourceService();

        SentenceMetadataResult result = SentenceMetadataProcessor.process(
            "", Map.of(), DEFAULT_HEDGE, DEFAULT_CONSTRAINT);
        assertNotNull(result);
        assertEquals(0, result.getDocSummary().getSentenceCount());
    }

    @Test
    void testWithoutModelsReturnsNull() {
        SentenceMetadataProcessor.configureResourceService(path -> Optional.empty());
        assertNull(SentenceMetadataProcessor.process(
            "Hello world.", Map.of(), DEFAULT_HEDGE, DEFAULT_CONSTRAINT));
    }

    private static void configureClasspathResourceService() {
        SentenceMetadataProcessor.configureResourceService(
            path -> Optional.ofNullable(SentenceMetadataProcessorTest.class.getResourceAsStream("/" + path)));
    }
}
