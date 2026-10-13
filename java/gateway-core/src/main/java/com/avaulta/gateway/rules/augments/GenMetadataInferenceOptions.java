package com.avaulta.gateway.rules.augments;

import lombok.Builder;
import lombok.Value;

/**
 * Per-call caps for genMetadata / classify inference. {@code null} fields mean use defaults
 * ({@link Augment.GenMetadata#DEFAULT_MAX_OUTPUT_TOKENS} /
 * {@link Augment.GenMetadata#DEFAULT_MAX_INPUT_TOKENS}).
 */
@Value
@Builder
public class GenMetadataInferenceOptions {

    Integer maxOutputTokens;
    Integer maxInputTokens;

    public static GenMetadataInferenceOptions defaults() {
        return builder().build();
    }
}
