package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Truncates genMetadata input so prompts fit local model context alongside reserved output tokens.
 */
@Singleton
public class GenMetadataPromptBudget {

    /** Rough chars-per-token for English text (conservative for JSON-escaped input). */
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    /** Template, labels, and chat formatting overhead beyond task + schema + input. */
    private static final int PROMPT_OVERHEAD_TOKENS = 64;

    private final ObjectMapper objectMapper;

    @Inject
    public GenMetadataPromptBudget(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String fitInputData(String inputData, String taskPrompt, JsonSchemaFilter outputSchema,
                               int maxInputChars, int contextLength, int maxOutputTokens) {
        String schemaJson = schemaJson(outputSchema);
        int contextChars = contextCharsBudget(contextLength, maxOutputTokens);
        int overheadChars = charEstimate(
            GenMetadataPromptBuilder.SYSTEM_PROMPT.length()
                + taskPrompt.length()
                + schemaJson.length()
                + PROMPT_OVERHEAD_TOKENS * CHARS_PER_TOKEN_ESTIMATE);
        int budgetChars = Math.min(maxInputChars, Math.max(0, contextChars - overheadChars));
        if (inputData.length() <= budgetChars) {
            return inputData;
        }
        return inputData.substring(0, budgetChars);
    }

    private int contextCharsBudget(int contextLength, int maxOutputTokens) {
        int outputReserve = Math.max(1, maxOutputTokens);
        int inputTokens = contextLength - outputReserve - PROMPT_OVERHEAD_TOKENS;
        return Math.max(0, inputTokens * CHARS_PER_TOKEN_ESTIMATE);
    }

    private int charEstimate(int chars) {
        return Math.max(0, chars);
    }

    private String schemaJson(JsonSchemaFilter outputSchema) {
        try {
            return objectMapper.writeValueAsString(outputSchema);
        } catch (Exception e) {
            return "{}";
        }
    }
}
