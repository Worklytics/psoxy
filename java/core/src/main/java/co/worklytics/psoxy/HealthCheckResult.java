package co.worklytics.psoxy;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class HealthCheckResult {

    static final int HTTP_SC_FAIL = 512;

    String configuredSource;

    boolean nonDefaultSalt;

    boolean passed() {
        return nonDefaultSalt && configuredSource != null;
    }

}
