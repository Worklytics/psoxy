package co.worklytics.psoxy.gateway;

import java.util.NoSuchElementException;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * POJO collecting configuration values for API data connector mode
 */
@Value
@Builder
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

        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.TARGET_HOST)
                .ifPresent(builder::targetHost);
        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER)
                .ifPresent(builder::sourceAuthStrategyIdentifier);
        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.TLS_VERSION)
                .ifPresent(builder::tlsVersion);
        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.ASYNC_OUTPUT_DESTINATION)
                .ifPresent(builder::asyncOutputDestination);
        configService.getConfigPropertyAsOptional(ApiModeConfigProperty.REQUEST_TIMEOUT_SECONDS)
                .map(Integer::parseInt)
                .ifPresent(builder::requestTimeoutSeconds);

        return builder.build();
    }

    /**
     * target 'Host' to forward requests to, in HTTP sense
     */
    String targetHost;

    /**
     * identifies the SourceAuthStrategy to use when connecting to the data source API
     */
    String sourceAuthStrategyIdentifier;

    /**
     * control the TLS protocol version used by proxy for outbound connections (eg, to data source)
     */
    @Builder.Default
    String tlsVersion = TlsVersions.TLSv1_3;

    /**
     * if provided, requests to proxy with `Prefer: respond-async` header will be processed
     * asynchronously and responses output to the target
     */
    String asyncOutputDestination;

    /**
     * overall request budget for a synchronous API data invocation, in seconds; should match the
     * Lambda / Cloud Function execution timeout configured in infrastructure
     */
    @Builder.Default
    int requestTimeoutSeconds = DEFAULT_REQUEST_TIMEOUT_SECONDS;

    public Optional<String> getTargetHost() {
        return Optional.ofNullable(targetHost);
    }

    public String getTargetHostOrError() {
        return getTargetHost().orElseThrow(() -> new NoSuchElementException(
                "Psoxy misconfigured. Expected value for: " + ApiModeConfigProperty.TARGET_HOST.name()));
    }

    public Optional<String> getSourceAuthStrategyIdentifier() {
        return Optional.ofNullable(sourceAuthStrategyIdentifier);
    }

    public Optional<String> getAsyncOutputDestination() {
        return Optional.ofNullable(asyncOutputDestination);
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
        int readTimeoutMs = (requestTimeoutSeconds - REQUEST_TIMEOUT_HEADROOM_SECONDS) * 1000;
        return Math.max(MIN_SOURCE_API_READ_TIMEOUT_MS, readTimeoutMs);
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
