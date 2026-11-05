package co.worklytics.psoxy.impl.gen;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;

/**
 * Caps the <em>dynamic</em> genMetadata corpus (serialized source match) in characters.
 *
 * <p>The static prefix — system text, task {@code prompt}, and schema/labels — is not counted.
 * That prefix is stable per augment rule and is split for provider prompt caching; identity is
 * {@link GenMetadataStaticPromptFingerprint}.
 *
 * <p>TODO: revisit with a real tokenizer when one is available on the classpath.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GenMetadataPromptBudget {

    /**
     * When an augment rule sets {@code maxInputTokens}, convert to a char cap (avg word + space).
     */
    static final int CHARS_PER_TOKEN_FOR_RULE_CAP = 5;

    private final GenMetadataConfig config;

    /**
     * Effective char budget for dynamic input: deployment cap, optionally tightened by the rule.
     */
    public int effectiveMaxDynamicInputChars(Integer ruleMaxInputTokens) {
        int cap = config.getMaxDynamicInputChars();
        if (ruleMaxInputTokens != null && ruleMaxInputTokens > 0) {
            cap = Math.min(cap, ruleMaxInputTokens * CHARS_PER_TOKEN_FOR_RULE_CAP);
        }
        return cap;
    }

    /**
     * Truncate serialized source to {@code maxChars}. When truncating, prefers a preceding
     * whitespace or punctuation boundary so a word is not split.
     */
    public String fitDynamicInput(String inputData, int maxChars) {
        if (inputData == null) {
            return null;
        }
        int budgetChars = Math.max(0, maxChars);
        if (inputData.length() <= budgetChars) {
            return inputData;
        }
        int cut = budgetChars;
        while (cut > 0 && Character.isLetterOrDigit(inputData.charAt(cut - 1))) {
            cut--;
        }
        if (cut == 0) {
            cut = budgetChars;
        }
        return inputData.substring(0, cut);
    }
}
