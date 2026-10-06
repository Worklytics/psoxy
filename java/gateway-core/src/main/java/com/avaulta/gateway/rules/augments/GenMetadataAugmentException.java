package com.avaulta.gateway.rules.augments;

import lombok.Getter;

/**
 * Runtime exception from genMetadata processing; translated to
 * {@link co.worklytics.psoxy.impl.AugmentProcessingException} in the core module.
 */
@Getter
public class GenMetadataAugmentException extends RuntimeException {

    public enum Code {
        UNAVAILABLE,
        INFERENCE_FAILED,
    }

    private final Code code;

    public GenMetadataAugmentException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public GenMetadataAugmentException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
