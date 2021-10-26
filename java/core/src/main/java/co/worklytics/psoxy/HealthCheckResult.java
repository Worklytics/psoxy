package co.worklytics.psoxy;


import co.worklytics.psoxy.gateway.ConfigService;
import lombok.*;

import java.util.Set;
import java.util.stream.Collectors;

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


    Set<ConfigService.ConfigProperty> missingConfigProperties;

    public Set<String> getMissingConfigProperties()  {
        if (missingConfigProperties == null ) {
            return null;
        } else {
            return missingConfigProperties.stream().map(ConfigService.ConfigProperty::name).collect(Collectors.toSet());
        }
    }


    boolean passed() {
        return configuredSource != null &&
            nonDefaultSalt &&
            (missingConfigProperties == null || missingConfigProperties.isEmpty());
    }

}
