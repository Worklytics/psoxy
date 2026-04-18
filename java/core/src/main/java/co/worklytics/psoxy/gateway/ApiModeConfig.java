package co.worklytics.psoxy.gateway;

import com.google.common.base.Splitter;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Configuration for REST API data connector mode: settings loaded once for the API proxy runtime.
 */
@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiModeConfig {

    @Builder.Default
    private final List<String> allowedDataAccessIpBlocks = Collections.emptyList();

    /**
     * Target host for upstream API requests (HTTP Host), if configured.
     */
    private final String targetHost;

    /**
     * TLS protocol version for outbound connections to the data source (never null).
     */
    @Builder.Default
    private final String tlsVersion = ApiModeConfigProperty.TlsVersions.TLSv1_3;

    private final String sourceAuthStrategyIdentifier;

    private final String asyncOutputDestination;

    public Optional<String> getTargetHost() {
        return Optional.ofNullable(targetHost).filter(s -> !s.isBlank());
    }

    public Optional<String> getSourceAuthStrategyIdentifier() {
        return Optional.ofNullable(sourceAuthStrategyIdentifier).filter(s -> !s.isBlank());
    }

    public Optional<String> getAsyncOutputDestination() {
        return Optional.ofNullable(asyncOutputDestination).filter(s -> !s.isBlank());
    }

    public static ApiModeConfig fromConfigService(ConfigService configService) {
        String csv = configService.getConfigPropertyAsOptional(ApiModeConfigProperty.ALLOWED_DATA_ACCESS_IP_BLOCKS)
                .orElse(null);

        List<String> list = StringUtils.isNotBlank(csv)
                ? Splitter.on(',').trimResults().omitEmptyStrings().splitToList(csv)
                : Collections.emptyList();

        String tlsRaw = configService.getConfigPropertyAsOptional(ApiModeConfigProperty.TLS_VERSION)
                .orElse(ApiModeConfigProperty.TlsVersions.TLSv1_3);
        if (Arrays.stream(ApiModeConfigProperty.TlsVersions.ALL).noneMatch(s -> tlsRaw.equals(s))) {
            throw new IllegalArgumentException("Invalid TLS version: " + tlsRaw);
        }

        return ApiModeConfig.builder()
                .allowedDataAccessIpBlocks(list)
                .targetHost(trimToNull(configService.getConfigPropertyAsOptional(ApiModeConfigProperty.TARGET_HOST).orElse(null)))
                .tlsVersion(tlsRaw)
                .sourceAuthStrategyIdentifier(trimToNull(
                        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER).orElse(null)))
                .asyncOutputDestination(trimToNull(
                        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.ASYNC_OUTPUT_DESTINATION).orElse(null)))
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
