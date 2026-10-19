package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Warning;
import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import lombok.Getter;

/**
 * Non-fatal augment failure to be caught by {@link AugmentProcessor} and surfaced as
 * {@code X-Psoxy-Warning} response headers.
 */
@Getter
public class AugmentProcessingException extends Exception {

    private final Warning warning;

    public AugmentProcessingException(Warning warning, String message) {
        super(message);
        this.warning = warning;
    }

    public AugmentProcessingException(Warning warning, String message, Throwable cause) {
        super(message, cause);
        this.warning = warning;
    }

    public static AugmentProcessingException from(GenMetadataAugmentException e) {
        Warning warning = switch (e.getCode()) {
            case UNAVAILABLE -> Warning.AUGMENT_GEN_UNAVAILABLE;
            case INFERENCE_FAILED -> Warning.AUGMENT_GEN_INFERENCE_FAILED;
        };
        return e.getCause() != null
            ? new AugmentProcessingException(warning, e.getMessage(), e.getCause())
            : new AugmentProcessingException(warning, e.getMessage());
    }

    public String getWarningCode() {
        return warning.asHttpHeaderCode();
    }
}
