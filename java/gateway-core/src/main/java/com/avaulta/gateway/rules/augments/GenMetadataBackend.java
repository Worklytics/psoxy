package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;

/**
 * Pluggable backend for {@link Augment.GenMetadata} inference.
 * Implementations use LangChain4j {@code ChatModel} adapters (Jlama for local embedded models;
 * Bedrock and Vertex planned for cloud).
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
}
