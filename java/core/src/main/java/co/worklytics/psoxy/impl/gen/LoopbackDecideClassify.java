package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.augments.EmbeddedDecideClassify;
import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import com.avaulta.gateway.rules.augments.GenMetadataInferenceOptions;
import com.avaulta.gateway.rules.augments.GenMetadataInferenceResult;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * {@link EmbeddedDecideClassify} for the v1 checkpoint: fit the state, one choice question,
 * loopback only. Does not call the chat backend. A missing checkpoint or a down server surfaces
 * as {@link GenMetadataAugmentException.Code#UNAVAILABLE} and does not fall through to remote-llm.
 *
 * <p>Token totals are recorded on the caller thread after the response is parsed, including a
 * missing {@code usage} object as zeros.
 */
@Log
@Singleton
public class LoopbackDecideClassify implements EmbeddedDecideClassify {

    private final ClassifyConfig config;
    private final GenMetadataPromptBudget promptBudget;
    private final GenMetadataTokenUsageAccumulator tokenUsageAccumulator;
    private final EmbeddedDecideRequest request;
    private final EmbeddedDecideParser parser;
    private final LoopbackDecideClient client;

    @Inject
    public LoopbackDecideClassify(ClassifyConfig config,
                                  GenMetadataPromptBudget promptBudget,
                                  GenMetadataTokenUsageAccumulator tokenUsageAccumulator,
                                  EmbeddedDecideRequest request,
                                  EmbeddedDecideParser parser,
                                  LoopbackDecideClient client) {
        this.config = config;
        this.promptBudget = promptBudget;
        this.tokenUsageAccumulator = tokenUsageAccumulator;
        this.request = request;
        this.parser = parser;
        this.client = client;
    }

    @Override
    public GenMetadataInferenceResult classify(String taskPrompt, List<String> classes,
                                               String inputData,
                                               GenMetadataInferenceOptions options) {
        List<String> labels = choiceLabels(classes);
        GenMetadataInferenceOptions effective =
            options != null ? options : GenMetadataInferenceOptions.defaults();
        int maxInputTokens = effectiveMaxInputTokens(effective.getMaxInputTokens());
        String fitted = promptBudget.fitDynamicInput(inputData, maxInputTokens);
        String body = request.toJson(taskPrompt, labels, fitted);
        String response = client.post(body, Duration.ofSeconds(config.getTimeoutSeconds()));
        if (response == null) {
            return null;
        }
        Optional<EmbeddedDecideChoice> parsed = parser.parse(response);
        if (parsed.isEmpty()) {
            return null;
        }
        EmbeddedDecideChoice choice = parsed.get();
        if (choice.isUsagePresent()) {
            tokenUsageAccumulator.record(choice.getInputTokens(), choice.getOutputTokens());
        } else {
            tokenUsageAccumulator.record(0, 0);
        }
        if (StringUtils.isBlank(choice.getLabel())) {
            return null;
        }
        log.info("classify embedded-decide choice=" + choice.getLabel()
            + " confidence=" + choice.getConfidence()
            + " probabilities=" + choice.getProbabilities());
        return GenMetadataInferenceResult.of(choice.getLabel());
    }

    /**
     * Trimmed class list in rule order. Embedded-decide requires
     * {@link ClassifyConfig#MIN_CHOICE_OPTIONS} to {@link ClassifyConfig#MAX_CHOICE_OPTIONS}
     * distinct labels.
     */
    static List<String> choiceLabels(List<String> classes) {
        if (classes == null) {
            throw unavailable("embedded-decide requires between "
                + ClassifyConfig.MIN_CHOICE_OPTIONS + " and "
                + ClassifyConfig.MAX_CHOICE_OPTIONS + " classes");
        }
        List<String> labels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String candidate : classes) {
            String trimmed = StringUtils.trimToNull(candidate);
            if (trimmed == null) {
                throw unavailable("embedded-decide class is blank");
            }
            if (!seen.add(trimmed)) {
                throw unavailable("embedded-decide class is duplicated: " + trimmed);
            }
            labels.add(trimmed);
        }
        if (labels.size() < ClassifyConfig.MIN_CHOICE_OPTIONS
            || labels.size() > ClassifyConfig.MAX_CHOICE_OPTIONS) {
            throw unavailable("embedded-decide requires between "
                + ClassifyConfig.MIN_CHOICE_OPTIONS + " and "
                + ClassifyConfig.MAX_CHOICE_OPTIONS + " classes, got " + labels.size());
        }
        return List.copyOf(labels);
    }

    private static int effectiveMaxInputTokens(Integer ruleMaxInputTokens) {
        if (ruleMaxInputTokens != null && ruleMaxInputTokens > 0) {
            return ruleMaxInputTokens;
        }
        return Augment.Classify.DEFAULT_MAX_INPUT_TOKENS;
    }

    private static GenMetadataAugmentException unavailable(String message) {
        return new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE, message);
    }
}
