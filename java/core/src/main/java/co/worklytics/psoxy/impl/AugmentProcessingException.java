package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.Warning;
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

    public String getWarningCode() {
        return warning.asHttpHeaderCode();
    }
}
