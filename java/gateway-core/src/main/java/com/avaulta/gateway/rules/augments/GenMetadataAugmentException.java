package com.avaulta.gateway.rules.augments;

/**
 * Runtime exception from genMetadata processing; translated to
 * {@link co.worklytics.psoxy.impl.AugmentProcessingException} in the core module.
 */
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

    public Code getCode() {
        return code;
    }
}
