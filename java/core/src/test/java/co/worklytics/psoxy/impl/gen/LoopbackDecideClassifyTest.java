package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import com.avaulta.gateway.rules.augments.GenMetadataInferenceOptions;
import com.avaulta.gateway.rules.augments.GenMetadataInferenceResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackDecideClassifyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void classify_fitsStateBeforeTheClientSeesIt() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        LoopbackDecideClassify classify = client((uri, json, timeout) -> {
            seen.set(json);
            return null;
        });
        String input = "abcdefghijklmnop";
        assertNull(classify.classify(
            "pick one",
            List.of("A", "B"),
            input,
            GenMetadataInferenceOptions.builder().maxInputTokens(2).build()));
        JsonNode state = objectMapper.readTree(seen.get()).get("state");
        assertEquals("abcdefgh", state.asText());
        assertTrue(state.asText().length() < input.length());
    }

    @Test
    void classify_rejectsTooFewOrTooManyClassesWithoutCallingLoopback() {
        AtomicInteger calls = new AtomicInteger();
        LoopbackDecideClassify classify = client((uri, json, timeout) -> {
            calls.incrementAndGet();
            return null;
        });
        GenMetadataAugmentException few = assertThrows(GenMetadataAugmentException.class,
            () -> classify.classify("p", List.of("only"), "x", GenMetadataInferenceOptions.defaults()));
        assertEquals(GenMetadataAugmentException.Code.UNAVAILABLE, few.getCode());
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < ClassifyConfig.MAX_CHOICE_OPTIONS + 1; i++) {
            tooMany.add("c" + i);
        }
        GenMetadataAugmentException many = assertThrows(GenMetadataAugmentException.class,
            () -> classify.classify("p", tooMany, "x", GenMetadataInferenceOptions.defaults()));
        assertEquals(GenMetadataAugmentException.Code.UNAVAILABLE, many.getCode());
        assertEquals(0, calls.get());
    }

    @Test
    void classify_recordsUsageAndReturnsLabel() throws Exception {
        String fixture;
        try (InputStream in = getClass().getResourceAsStream("/embedded-decide/choice-response.json")) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        GenMetadataTokenUsageAccumulator tokens = new GenMetadataTokenUsageAccumulator();
        LoopbackDecideClassify classify = client(tokens, (uri, json, timeout) -> fixture);
        GenMetadataInferenceResult result = classify.classify(
            "Classify the input into exactly one class.",
            List.of("Email Drafting", "Research and Ideation", "Uncategorized", "Excluded"),
            "draft the renewal",
            GenMetadataInferenceOptions.defaults());
        assertEquals("Email Drafting", result.getText());
        assertEquals(120, tokens.snapshot().getInputTokens());
        assertEquals(1, tokens.snapshot().getOutputTokens());
        assertEquals(1, tokens.snapshot().getCalls());
    }

    @Test
    void classify_missingChoiceIsEmptySoTheProcessorCanRetry() {
        LoopbackDecideClassify classify = client((uri, json, timeout) -> """
            {"answers":{"label":{"type":"choice","confidence":0.0}},"usage":{"input_tokens":4,"output_tokens":1}}
            """);
        assertNull(classify.classify(
            "p", List.of("A", "B"), "x", GenMetadataInferenceOptions.defaults()));
    }

    private LoopbackDecideClassify client(LoopbackDecideClient.Poster poster) {
        return client(new GenMetadataTokenUsageAccumulator(), poster);
    }

    private LoopbackDecideClassify client(GenMetadataTokenUsageAccumulator tokens,
                                          LoopbackDecideClient.Poster poster) {
        ClassifyConfig config = ClassifyConfig.from(new co.worklytics.psoxy.gateway.ConfigService() {
            @Override
            public String getConfigPropertyOrError(ConfigProperty property) {
                return getConfigPropertyAsOptional(property).orElseThrow();
            }

            @Override
            public java.util.Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
                if (property == ClassifyConfig.ConfigProperty.CLASSIFY_IMPLEMENTATION) {
                    return java.util.Optional.of("embedded-decide");
                }
                if (property == ClassifyConfig.ConfigProperty.CLASSIFY_MODEL_REVISION) {
                    return java.util.Optional.of("261030");
                }
                return java.util.Optional.empty();
            }
        });
        return new LoopbackDecideClassify(
            config,
            new GenMetadataPromptBudget(),
            tokens,
            new EmbeddedDecideRequest(objectMapper),
            new EmbeddedDecideParser(objectMapper),
            new LoopbackDecideClient(poster));
    }
}
