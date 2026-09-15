package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;

/**
 * Default backend when genMetadata is not configured or the selected backend is unsupported.
 */
public class UnavailableGenMetadataBackend implements GenMetadataBackend {

    @Override
    public Object generate(String taskPrompt, JsonSchemaFilter outputSchema, String inputData) {
        throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
            "genMetadata backend is not available");
    }
}
