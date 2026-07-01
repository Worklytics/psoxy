package co.worklytics.psoxy.gateway;

/**
 * Signals that a config/secret backend had a transient failure (credential rotation, network
 * blip, service hiccup) and the value may still be accessible on the next attempt.
 *
 * Distinct from a missing value ({@code Optional.empty()} / {@code NEGATIVE_VALUE}): callers
 * should NOT treat this as "property not configured" — they should retry or serve a cached value.
 */
public class TransientConfigException extends RuntimeException {

    public TransientConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public TransientConfigException(String message) {
        super(message);
    }
}
