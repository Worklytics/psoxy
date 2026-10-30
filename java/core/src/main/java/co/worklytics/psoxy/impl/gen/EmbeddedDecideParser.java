package co.worklytics.psoxy.impl.gen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parses a v1 decider choice response. A body that is not that shape, or a choice answer with no
 * {@code choice} string, is empty so the caller can retry.
 */
@Singleton
public class EmbeddedDecideParser {

    private final ObjectMapper objectMapper;

    @Inject
    public EmbeddedDecideParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<EmbeddedDecideChoice> parse(String json) {
        if (StringUtils.isBlank(json)) {
            return Optional.empty();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
        if (root == null || !root.isObject()) {
            return Optional.empty();
        }
        JsonNode answers = root.get("answers");
        if (answers == null || !answers.isObject()) {
            return Optional.empty();
        }
        JsonNode answer = answers.get(EmbeddedDecideRequest.QUESTION_KEY);
        if (answer == null || !answer.isObject()) {
            return Optional.empty();
        }
        String label = null;
        JsonNode choice = answer.get("choice");
        if (choice != null && choice.isTextual()) {
            label = StringUtils.trimToNull(choice.asText());
        }
        Double confidence = null;
        JsonNode confidenceNode = answer.get("confidence");
        if (confidenceNode != null && confidenceNode.isNumber()) {
            confidence = confidenceNode.doubleValue();
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        JsonNode probabilityNode = answer.get("probabilities");
        if (probabilityNode != null && probabilityNode.isObject()) {
            probabilityNode.fields().forEachRemaining(entry -> {
                if (entry.getValue().isNumber()) {
                    probabilities.put(entry.getKey(), entry.getValue().doubleValue());
                }
            });
        }
        JsonNode usage = root.get("usage");
        boolean usagePresent = usage != null && usage.isObject();
        int inputTokens = 0;
        int outputTokens = 0;
        if (usagePresent) {
            inputTokens = usage.path("input_tokens").asInt(0);
            outputTokens = usage.path("output_tokens").asInt(0);
        }
        return Optional.of(new EmbeddedDecideChoice(
            label, confidence, Map.copyOf(probabilities), usagePresent, inputTokens, outputTokens));
    }
}
