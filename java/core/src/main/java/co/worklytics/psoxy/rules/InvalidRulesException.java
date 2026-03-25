package co.worklytics.psoxy.rules;

import co.worklytics.psoxy.ErrorCauses;
import lombok.Getter;

public class InvalidRulesException extends RuntimeException {
    
    @Getter
    private final ErrorCauses errorCause;
    
    public InvalidRulesException(String message, ErrorCauses errorCause) {
        super(message);
        this.errorCause = errorCause;
    }
    
    public InvalidRulesException(String message, Throwable cause, ErrorCauses errorCause) {
        super(message, cause);
        this.errorCause = errorCause;
    }
}
