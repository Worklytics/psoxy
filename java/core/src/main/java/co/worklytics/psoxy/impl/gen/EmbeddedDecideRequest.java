package co.worklytics.psoxy.impl.gen;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * One {@code !<classify>} rule as one choice question on the v1 decider server.
 *
 * <p>{@code state} is the fitted serialized match. {@code instructions} is the rule prompt.
 * {@code criteria} is {@code classes} in order, each with a null description: classify YAML has
 * labels, not per-class descriptions. The question key is {@link #QUESTION_KEY}. No other
 * question type is written.
 */
@Singleton
public class EmbeddedDecideRequest {

    /** Single question name. The sibling augment property stays one string; this key is not written. */
    public static final String QUESTION_KEY = "label";

    private final ObjectMapper objectMapper;

    @Inject
    public EmbeddedDecideRequest(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(@NonNull String instructions, @NonNull List<String> classes, String state) {
        ObjectNode criteria = objectMapper.createObjectNode();
        for (String label : classes) {
            criteria.putNull(label);
        }
        ObjectNode question = objectMapper.createObjectNode();
        question.put("type", "choice");
        question.put("instructions", instructions);
        question.set("criteria", criteria);
        ObjectNode questions = objectMapper.createObjectNode();
        questions.set(QUESTION_KEY, question);
        ObjectNode root = objectMapper.createObjectNode();
        root.put("state", state == null ? "" : state);
        root.set("questions", questions);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode embedded-decide choice", e);
        }
    }
}
