package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchema;
import lombok.NonNull;

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
     * @return model text, or {@code null} if inference produced nothing
     */
    default GenMetadataInferenceResult generate(String taskPrompt, JsonSchema outputSchema,
                                                String inputData) {
        return generate(taskPrompt, outputSchema, inputData, GenMetadataInferenceOptions.defaults());
    }

    /**
     * Same as {@link #generate(String, JsonSchema, String)} with per-call token caps in
     * {@code options}.
     */
    GenMetadataInferenceResult generate(String taskPrompt, JsonSchema outputSchema, String inputData,
                                        GenMetadataInferenceOptions options);

    /**
     * Closed-set classify. Default delegates to {@link #generate} with a string-enum schema so
     * test doubles that only implement {@link #generate} still work.
     */
    default GenMetadataInferenceResult classify(String taskPrompt, @NonNull List<String> classes,
                                                String inputData,
                                                GenMetadataInferenceOptions options) {
        JsonSchema schema = JsonSchema.builder()
            .type("string")
            .enumValues(classes)
            .build();
        return generate(taskPrompt, schema, inputData, options);
    }
}
