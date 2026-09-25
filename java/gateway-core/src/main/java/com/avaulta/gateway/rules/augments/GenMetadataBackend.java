package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchema;

import java.util.List;

/**
 * Pluggable backend for {@link Augment.GenMetadata} and {@link Augment.Classify} inference.
 * Implementations use LangChain4j {@code ChatModel} adapters for cloud backends
 * (Bedrock on AWS, Vertex AI on GCP).
 */
public interface GenMetadataBackend {

    /**
     * Generate JSON metadata for the given input.
     *
     * @param taskPrompt augment rule task prompt
     * @param outputSchema required output schema predicate
     * @param inputData JSON-serialized source value
     * @return parsed JSON object (typically a Map), or raw JSON string from the model
     */
    Object generate(String taskPrompt, JsonSchema outputSchema, String inputData);

    /**
     * Same as {@link #generate(String, JsonSchema, String)} with optional per-call generation
     * and dynamic-input token caps. {@code null} means use
     * {@link Augment.GenMetadata#DEFAULT_MAX_OUTPUT_TOKENS} /
     * {@link Augment.GenMetadata#DEFAULT_MAX_INPUT_TOKENS}.
     */
    default Object generate(String taskPrompt, JsonSchema outputSchema, String inputData,
                            Integer maxOutputTokens) {
        return generate(taskPrompt, outputSchema, inputData, maxOutputTokens, null);
    }

    default Object generate(String taskPrompt, JsonSchema outputSchema, String inputData,
                            Integer maxOutputTokens, Integer maxInputTokens) {
        return generate(taskPrompt, outputSchema, inputData);
    }

    /**
     * Closed-set classify. Default delegates to {@link #generate} with a string-enum schema so
     * test doubles that only implement {@link #generate} still work.
     */
    default Object classify(String taskPrompt, List<String> classes, String inputData,
                            int maxOutputTokens, Integer maxInputTokens) {
        JsonSchema schema = JsonSchema.builder()
            .type("string")
            .enumValues(classes)
            .build();
        return generate(taskPrompt, schema, inputData, maxOutputTokens, maxInputTokens);
    }
}
