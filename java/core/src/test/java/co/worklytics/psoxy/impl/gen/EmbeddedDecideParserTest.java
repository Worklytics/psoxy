package co.worklytics.psoxy.impl.gen;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddedDecideParserTest {

    private final EmbeddedDecideParser parser = new EmbeddedDecideParser(new com.fasterxml.jackson.databind.ObjectMapper());

    @Test
    void parse_fixtureYieldsLabelConfidenceAndTokens() throws Exception {
        String json;
        try (InputStream in = getClass().getResourceAsStream("/embedded-decide/choice-response.json")) {
            json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Optional<EmbeddedDecideChoice> parsed = parser.parse(json);
        assertTrue(parsed.isPresent());
        EmbeddedDecideChoice choice = parsed.get();
        assertEquals("Email Drafting", choice.getLabel());
        assertEquals(0.88, choice.getConfidence());
        assertEquals(0.91, choice.getProbabilities().get("Email Drafting"));
        assertTrue(choice.isUsagePresent());
        assertEquals(120, choice.getInputTokens());
        assertEquals(1, choice.getOutputTokens());
    }

    @Test
    void parse_missingChoiceIsEmptyLabel() {
        String json = """
            {"model":"strands-decider-2B-qwen3.5-v1-2610","answers":{"label":{"type":"choice","probabilities":{"A":0.5,"B":0.5},"confidence":0.0}},"usage":{"input_tokens":10,"output_tokens":1}}
            """;
        Optional<EmbeddedDecideChoice> parsed = parser.parse(json);
        assertTrue(parsed.isPresent());
        assertNull(parsed.get().getLabel());
        assertTrue(parsed.get().isUsagePresent());
        assertEquals(10, parsed.get().getInputTokens());
    }

    @Test
    void parse_malformedBodyIsEmpty() {
        assertFalse(parser.parse("not-json").isPresent());
        assertFalse(parser.parse("{\"answers\":{}}").isPresent());
        assertFalse(parser.parse(null).isPresent());
    }
}
