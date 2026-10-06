package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.resources.ResourceService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class TextMetadataProcessorTest {

    private static final Set<String> DEFAULT_HEDGE = Set.copyOf(Augment.TextMetadata.DEFAULT_HEDGE_WORDS);
    private static final Set<String> DEFAULT_CONSTRAINT = Set.copyOf(Augment.TextMetadata.DEFAULT_CONSTRAINT_WORDS);

    private static final String MODELS_MISSING_MESSAGE =
        "OpenNLP models not on classpath (expected at /opennlp/en-sent.bin). "
            + "Run 'mvn test -DskipOpenNlpModelDownload=false' in java/gateway-core to download them, "
            + "or run tools/fetch-opennlp-models.sh manually.";

    @Test
    void analyzeSentence_derivesSignalsAndTaxonomyWithoutModels() {
        Map<String, String> taxonomy = Map.of("email", "MEDIUM");

        SentenceAnalysis analysis = TextMetadataProcessor.analyzeSentence(
            0,
            new String[] {"Could", "you", "avoid", "sending", "an", "email", "?"},
            new String[] {"MD", "PRP", "VB", "VBG", "DT", "NN", "."},
            new String[] {"B-VP", "I-VP", "I-VP", "I-VP", "B-NP", "I-NP", "O"},
            taxonomy,
            DEFAULT_HEDGE,
            DEFAULT_CONSTRAINT);

        assertEquals("interrogative", analysis.sentence().getType());
        assertNotNull(analysis.sentence().getSignals());
        assertTrue(analysis.sentence().getSignals().isQuestion());
        assertTrue(analysis.sentence().getSignals().isConstraint());
        assertNotNull(analysis.sentence().getNouns());
        assertEquals(1, analysis.sentence().getNouns().size());
        assertEquals("email", analysis.sentence().getNouns().getFirst().getNoun());
        assertEquals("MEDIUM", analysis.sentence().getNouns().getFirst().getCategory());
        assertTrue(analysis.nounCategories().contains("MEDIUM"));
    }

    @Test
    void testProcessWithModels() {
        assertModelsAvailable();
        TextMetadataProcessor processor = processorWithClasspathModels();

        Map<String, List<String>> taxonomy = new TreeMap<>();
        taxonomy.put("CODE_ARTIFACT", List.of("code", "script", "function", "api"));
        taxonomy.put("MEDIUM", List.of("email", "message"));

        String text = "Please write a python script! Could you avoid sending an email?";

        TextMetadataResult result = processor.process(
            text, taxonomy, DEFAULT_HEDGE, DEFAULT_CONSTRAINT);

        assertNotNull(result);
        assertNotNull(result.getSentences());
        assertEquals(2, result.getSentences().size());

        TextMetadataResult.Sentence s2 = result.getSentences().get(1);
        assertEquals("interrogative", s2.getType());
        assertNotNull(s2.getSignals());
        assertTrue(s2.getSignals().isQuestion());
        assertTrue(s2.getSignals().isConstraint());

        assertNotNull(s2.getNouns());
        boolean foundMedium = s2.getNouns().stream()
            .anyMatch(n -> "MEDIUM".equals(n.getCategory()) && "email".equals(n.getNoun()));
        assertTrue(foundMedium);

        TextMetadataResult.DocSummary docSummary = result.getDocSummary();
        assertNotNull(docSummary);
        assertEquals(2, docSummary.getSentenceCount());
        assertTrue(docSummary.isAnyQuestion());
        assertTrue(docSummary.isAnyConstraint());
        assertNotNull(docSummary.getNounCategories());
        assertTrue(docSummary.getNounCategories().contains("MEDIUM"));
    }

    @Test
    void testProcessWithInvalidModelBytes() {
        byte[] stubModel = "stub-model".getBytes(StandardCharsets.UTF_8);
        TextMetadataProcessor processor = new TextMetadataProcessor(path -> {
            if (path.startsWith("opennlp/")) {
                return Optional.of(new ByteArrayInputStream(stubModel));
            }
            return Optional.empty();
        });

        assertNull(processor.process(
            "Hello world.", Map.of(), DEFAULT_HEDGE, DEFAULT_CONSTRAINT));
    }

    @Test
    void testEmptyText() {
        assertModelsAvailable();
        TextMetadataProcessor processor = processorWithClasspathModels();

        TextMetadataResult result = processor.process(
            "", Map.of(), DEFAULT_HEDGE, DEFAULT_CONSTRAINT);
        assertNotNull(result);
        assertNotNull(result.getDocSummary());
        assertEquals(0, result.getDocSummary().getSentenceCount());
    }

    @Test
    void testWithoutModelsReturnsNull() {
        TextMetadataProcessor processor = new TextMetadataProcessor(path -> Optional.empty());
        assertNull(processor.process(
            "Hello world.", Map.of(), DEFAULT_HEDGE, DEFAULT_CONSTRAINT));
    }

    private static void assertModelsAvailable() {
        assertNotNull(
            TextMetadataProcessorTest.class.getResourceAsStream("/opennlp/en-sent.bin"),
            MODELS_MISSING_MESSAGE);
    }

    private static TextMetadataProcessor processorWithClasspathModels() {
        ResourceService resourceService =
            path -> Optional.ofNullable(TextMetadataProcessorTest.class.getResourceAsStream("/" + path));
        return new TextMetadataProcessor(resourceService);
    }
}
