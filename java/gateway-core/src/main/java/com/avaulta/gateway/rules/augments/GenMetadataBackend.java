package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;

/**
 * Pluggable backend for {@link Augment.GenMetadata} inference.
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
    Object generate(String taskPrompt, JsonSchemaFilter outputSchema, String inputData);

    /**
     * Same as {@link #generate(String, JsonSchemaFilter, String)} with optional per-call generation
     * and dynamic-input token caps. {@code null} means use
     * {@link Augment.GenMetadata#DEFAULT_MAX_TOKENS} /
     * {@link Augment.GenMetadata#DEFAULT_MAX_INPUT_TOKENS}.
     */
    default Object generate(String taskPrompt, JsonSchemaFilter outputSchema, String inputData,
                            Integer maxOutputTokens) {
        return generate(taskPrompt, outputSchema, inputData, maxOutputTokens, null);
    }

    default Object generate(String taskPrompt, JsonSchemaFilter outputSchema, String inputData,
                            Integer maxOutputTokens, Integer maxInputTokens) {
        return generate(taskPrompt, outputSchema, inputData);
    }
}
