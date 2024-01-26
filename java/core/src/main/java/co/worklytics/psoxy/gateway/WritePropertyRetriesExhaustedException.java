package co.worklytics.psoxy.gateway;

/**
 * Used to indicate that a write operation failed after exhausting all retries.
 * Checked to enforce the caller to handle the exception.
 */
public class WritePropertyRetriesExhaustedException extends Exception {

    public WritePropertyRetriesExhaustedException(String message) {
        super(message);
    }

    public WritePropertyRetriesExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
