package co.worklytics.psoxy;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;

import java.util.Set;

@JsonPropertyOrder(alphabetic = true) //for consistent json format across jdks
@Data
@Builder
@NoArgsConstructor //for jackson
@AllArgsConstructor
public class HealthCheckResult {

    @RequiredArgsConstructor
    enum HttpStatusCode {
        SUCCEED(200),
        FAIL(512);

        @Getter
        private final int code;
    }

    String configuredSource;

    Boolean nonDefaultSalt;

    Set<String> missingConfigProperties;

    boolean passed() {
        return getConfiguredSource() != null
            && getNonDefaultSalt()
            && getMissingConfigProperties().isEmpty();
    }

    //TODO: to make this robust across versions, add a JsonAnySetter handler to catch unknown stuff?
}
