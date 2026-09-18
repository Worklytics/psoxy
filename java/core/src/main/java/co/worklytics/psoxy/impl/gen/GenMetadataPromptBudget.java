package co.worklytics.psoxy.impl.gen;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.NoArgsConstructor;

/**
 * Caps the <em>dynamic</em> genMetadata corpus (serialized source match) in estimated tokens.
 *
 * <p>The static prefix — system text, task {@code prompt}, and schema/labels — is not counted.
 * That prefix is stable per augment rule and is the candidate for future provider prompt caching.
 *
 * <p>Estimate is {@value #CHARS_PER_TOKEN_ESTIMATE} chars/token, not a model tokenizer.
 *
 * <p>TODO: use a real tokenizer (e.g. langchain4j's OpenAiTokenCountEstimator / jtokkit) when
 * one is available on the classpath.
 */
@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class GenMetadataPromptBudget {

    /** Rough chars-per-token for English / JSON-escaped input. */
    static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    /**
     * Truncate serialized source so it fits {@code maxInputTokens} estimated tokens.
     */
    public String fitDynamicInput(String inputData, int maxInputTokens) {
        if (inputData == null) {
            return null;
        }
        int budgetChars = Math.max(0, maxInputTokens) * CHARS_PER_TOKEN_ESTIMATE;
        if (inputData.length() <= budgetChars) {
            return inputData;
        }
        return inputData.substring(0, budgetChars);
    }
}
