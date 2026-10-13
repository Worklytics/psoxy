package com.avaulta.gateway.rules.augments;

import lombok.Builder;
import lombok.Value;

/**
 * Cloud inference output we control, rather than exposing LangChain4j's loosely typed chat result.
 */
@Value
@Builder
public class GenMetadataInferenceResult {

    /** Raw model text; {@code null} if the call produced no usable content. */
    String text;

    public static GenMetadataInferenceResult of(String text) {
        return builder().text(text).build();
    }
}
