package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchema;

/**
 * Default backend when genMetadata is not configured or the selected backend is unsupported.
 */
public class UnavailableGenMetadataBackend implements GenMetadataBackend {

    @Override
    public GenMetadataInferenceResult generate(String taskPrompt, JsonSchema outputSchema,
                                               String inputData,
                                               GenMetadataInferenceOptions options) {
        throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
            "genMetadata backend is not available");
    }
}
