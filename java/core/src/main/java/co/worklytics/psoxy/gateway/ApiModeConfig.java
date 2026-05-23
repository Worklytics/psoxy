package co.worklytics.psoxy.gateway;

import com.google.common.base.Splitter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

/**
 * POJO collecting configuration values for API data connector mode
 */
@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiModeConfig {

    public static final int DEFAULT_REQUEST_TIMEOUT_SECONDS = 180;

    private static final int REQUEST_TIMEOUT_HEADROOM_SECONDS = 30;
    private static final int SOURCE_API_CONNECT_TIMEOUT_MS = 30_000;
    private static final int MIN_SOURCE_API_READ_TIMEOUT_MS = 60_000;

    /**
     * Factory method to build config from ConfigService
     */
    public static ApiModeConfig fromConfigService(ConfigService configService) {
        ApiModeConfigBuilder builder = ApiModeConfig.builder();

        String csv = configService.getConfigPropertyAsOptional(ApiModeConfigProperty.ALLOWED_DATA_ACCESS_IP_BLOCKS)
                .orElse(null);
        List<String> allowedIpBlocks = StringUtils.isNotBlank(csv)
                ? Splitter.on(',').trimResults().omitEmptyStrings().splitToList(csv)
                : Collections.emptyList();
        builder.allowedDataAccessIpBlocks(allowedIpBlocks);

        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.TARGET_HOST)
                .map(StringUtils::trimToNull)
                .ifPresent(builder::targetHost);
        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER)
                .map(StringUtils::trimToNull)
                .ifPresent(builder::sourceAuthStrategyIdentifier);
        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.ASYNC_OUTPUT_DESTINATION)
                .map(StringUtils::trimToNull)
                .ifPresent(builder::asyncOutputDestination);
        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.REQUEST_TIMEOUT_SECONDS)
                .map(value -> ConfigService.parseIntValue(ApiModeConfigProperty.REQUEST_TIMEOUT_SECONDS, value))
                .ifPresent(builder::requestTimeoutSeconds);

        String tlsRaw = configService.getConfigPropertyAsOptional(ApiModeConfigProperty.TLS_VERSION)
                .orElse(TlsVersions.TLSv1_3);
        if (Arrays.stream(TlsVersions.ALL).noneMatch(s -> tlsRaw.equals(s))) {
            throw new IllegalArgumentException("Invalid TLS version: " + tlsRaw);
        }
        builder.tlsVersion(tlsRaw);

        return builder.build();
    }

    /**
     * IPs or CIDR blocks allowed for API data access (application-layer allowlist).
     */
    @Builder.Default
    private final List<String> allowedDataAccessIpBlocks = Collections.emptyList();

    /**
     * target 'Host' to forward requests to, in HTTP sense
     */
    private final String targetHost;

    /**
     * identifies the SourceAuthStrategy to use when connecting to the data source API
     */
    private final String sourceAuthStrategyIdentifier;

    /**
     * control the TLS protocol version used by proxy for outbound connections (eg, to data source)
     */
    @Builder.Default
    private final String tlsVersion = TlsVersions.TLSv1_3;

    /**
     * if provided, requests to proxy with `Prefer: respond-async` header will be processed
     * asynchronously and responses output to the target
     */
    private final String asyncOutputDestination;

    /**
     * overall request budget for a synchronous API data invocation, in seconds; should match the
     * Lambda / Cloud Function execution timeout configured in infrastructure
     */
    @Builder.Default
    private final int requestTimeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;

    public Optional<String> getTargetHost() {
        return Optional.ofNullable(targetHost).filter(s -> !s.isBlank());
    }

    public String getTargetHostOrError() {
        return getTargetHost().orElseThrow(() -> new NoSuchElementException(
                "Psoxy misconfigured. Expected value for: " + ApiModeConfigProperty.TARGET_HOST.name()));
    }

    public Optional<String> getSourceAuthStrategyIdentifier() {
        return Optional.ofNullable(sourceAuthStrategyIdentifier).filter(s -> !s.isBlank());
    }

    public Optional<String> getAsyncOutputDestination() {
        return Optional.ofNullable(asyncOutputDestination).filter(s -> !s.isBlank());
    }

    /**
     * connect timeout for outbound requests to the source API, in milliseconds
     */
    public int getSourceApiConnectTimeoutMs() {
        int requestTimeoutMs = requestTimeoutSeconds * 1000;
        return Math.min(SOURCE_API_CONNECT_TIMEOUT_MS, requestTimeoutMs);
    }

    /**
     * read timeout for outbound requests to the source API, in milliseconds
     */
    public int getSourceApiReadTimeoutMs() {
        int requestTimeoutMs = requestTimeoutSeconds * 1000;
        int readTimeoutMs = Math.max(MIN_SOURCE_API_READ_TIMEOUT_MS,
                (requestTimeoutSeconds - REQUEST_TIMEOUT_HEADROOM_SECONDS) * 1000);
        return Math.min(readTimeoutMs, requestTimeoutMs);
    }

    /**
     * possible values for TLS_VERSION config property
     */
    public static class TlsVersions {
        public static final String TLSv1_2 = "TLSv1.2";
        public static final String TLSv1_3 = "TLSv1.3";
        public static final String[] ALL = {TLSv1_2, TLSv1_3};
    }

    @NoArgsConstructor
    @AllArgsConstructor
    public enum ApiModeConfigProperty implements ConfigService.ConfigProperty {

        ALLOWED_DATA_ACCESS_IP_BLOCKS,

        ASYNC_OUTPUT_DESTINATION,

        SOURCE_AUTH_STRATEGY_IDENTIFIER,

        TLS_VERSION,

        TARGET_HOST,

        REQUEST_TIMEOUT_SECONDS,
        ;

        @Getter(onMethod_ = @Override)
        private SupportedSource supportedSource = SupportedSource.ENV_VAR;
    }
}
