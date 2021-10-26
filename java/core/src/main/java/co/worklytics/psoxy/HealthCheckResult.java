package co.worklytics.psoxy;


import lombok.*;

@Data
@Builder
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

    boolean nonDefaultSalt;

    boolean passed() {
        return nonDefaultSalt && configuredSource != null;
    }

}
