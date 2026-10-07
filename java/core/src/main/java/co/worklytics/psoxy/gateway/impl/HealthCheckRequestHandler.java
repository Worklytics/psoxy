package co.worklytics.psoxy.gateway.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.ErrorCauses;
import co.worklytics.psoxy.HashUtils;
import co.worklytics.psoxy.HealthCheckResult;
import co.worklytics.psoxy.ProcessedDataMetadataFields;
import co.worklytics.psoxy.gateway.ApiModeConfig;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ConfigStoreUnreachableException;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.NetworkConnectivityFailures;
import co.worklytics.psoxy.gateway.NetworkSecurityUtils;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.ProxyConstants;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.rules.RulesUtils;
import dagger.Lazy;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;


/**
 * Request handler that performs health check duties
 */
@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class HealthCheckRequestHandler {

    @Inject
    ApiModeConfig apiModeConfig;
    @Inject
    EnvVarsConfigService envVarsConfigService;
    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;
    @Inject
    Lazy<SourceAuthStrategy> sourceAuthStrategy;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    RulesUtils rulesUtils;
    @Inject
    HashUtils hashUtils;
    @Inject
    ProxyConstants proxyConstants;
    @Inject
    NetworkSecurityUtils networkSecurityUtils;

    volatile String piiSaltHash;
    private final Object $piiSaltHashLock = new Object[0];

    public Optional<HttpEventResponse> handleIfHealthCheck(HttpEventRequest request) {
        if (isHealthCheckRequest(request)) {
            if (request.getClientIp().isPresent()) {
                log.info("Health check request from " + request.getClientIp().get());
            }
            return Optional.of(handle(request));
        }
        return Optional.empty();
    }

    private boolean isHealthCheckRequest(HttpEventRequest request) {
        return request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()).isPresent();
    }

    private void logInDev(String message, Throwable e) {
        if (envVarsConfigService.isDevelopment()) {
            log.log(Level.WARNING, message, e);
        }
    }

    private static final ObjectMapper STARTUP_OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private static final String CONFIG_STORE_UNREACHABLE_SUMMARY =
            "Unable to reach the configuration store. Outbound connectivity from this function is blocked. "
                    + "On AWS, the VPC interface endpoint or NAT for SSM Parameter Store or Secrets Manager "
                    + "is missing or unreachable, or a security group blocks HTTPS to it. "
                    + "On GCP, a Cloud Function with VPC egress set to all traffic needs Private Google Access "
                    + "or Cloud NAT to reach Secret Manager. "
                    + "Search logs for \"Error reading configuration from SSM\" or "
                    + "\"Error reading configuration from Secret Manager\".";

    private static final String DEPENDENT_SERVICE_UNREACHABLE_SUMMARY =
            "Failed to connect to a dependent service. The underlying error is included here and in the logs.";

    private HttpEventResponse handle(HttpEventRequest request) {
        try {
            return buildHealthCheck(request);
        } catch (Throwable e) {
            if (!NetworkConnectivityFailures.isConnectivityFailure(e)) {
                if (e instanceof Error error) {
                    throw error;
                }
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new RuntimeException(e);
            }
            return unreachable(callerIp(request), e);
        }
    }

    public static boolean isConfigStoreFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConfigStoreUnreachableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * 503 health-check body. A {@link ConfigStoreUnreachableException} in the cause chain is
     * {@code CONFIG_STORE_UNREACHABLE}. Any other connectivity failure is
     * {@code DEPENDENT_SERVICE_UNREACHABLE}; the client error is logged and copied into the body
     * so the dependency is identified from that message.
     */
    public static HttpEventResponse unreachable(String callerIp, Throwable failure) {
        if (isConfigStoreFailure(failure)) {
            log.log(Level.SEVERE, "Failed to reach the configuration store", failure);
            return configStoreUnreachable(callerIp, failure);
        }
        log.log(Level.SEVERE, "Failed to connect to a dependent service", failure);
        return dependentServiceUnreachable(callerIp, failure);
    }

    /**
     * JSON health-check body plus {@code X-Psoxy-Error: CONFIG_STORE_UNREACHABLE}.
     * Only for failures already identified as the configuration store.
     */
    public static HttpEventResponse configStoreUnreachable(String callerIp, Throwable failure) {
        return connectivityResponse(callerIp, failure, ErrorCauses.CONFIG_STORE_UNREACHABLE,
                CONFIG_STORE_UNREACHABLE_SUMMARY);
    }

    public static HttpEventResponse dependentServiceUnreachable(String callerIp, Throwable failure) {
        return connectivityResponse(callerIp, failure, ErrorCauses.DEPENDENT_SERVICE_UNREACHABLE,
                DEPENDENT_SERVICE_UNREACHABLE_SUMMARY);
    }

    private static HttpEventResponse connectivityResponse(String callerIp, Throwable failure,
            ErrorCauses cause, String summary) {
        String detail = NetworkConnectivityFailures.describe(failure);
        HealthCheckResult result = HealthCheckResult.builder()
                .javaSourceCodeVersion(ProxyConstants.JAVA_SOURCE_CODE_VERSION)
                .callerIp(callerIp)
                .nonDefaultSalt(false)
                .missingConfigProperties(Set.of())
                .error(cause.name())
                .warningMessage(summary + " Underlying error: " + detail)
                .build();

        HttpEventResponse.HttpEventResponseBuilder responseBuilder = HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CONTENT_TYPE,
                        ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8).getMimeType())
                .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(), cause.name());
        try {
            String json = STARTUP_OBJECT_MAPPER.writeValueAsString(result);
            responseBuilder.body(json + "\r\n");
            log.warning("Health check failed: " + json);
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to write connectivity health check details", e);
            responseBuilder.body("{\"error\":\"" + cause.name() + "\"}\r\n");
        }
        return responseBuilder.build();
    }

    public static String callerIp(HttpEventRequest request) {
        try {
            if (request == null) {
                return "unknown";
            }
            return request.getClientIp().orElse("unknown");
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static void rethrowIfConnectivityFailure(Throwable e) {
        if (!NetworkConnectivityFailures.isConnectivityFailure(e)) {
            return;
        }
        if (e instanceof Error error) {
            throw error;
        }
        if (e instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new RuntimeException(e);
    }

    private HttpEventResponse buildHealthCheck(HttpEventRequest request) {

        Set<String> missing = new HashSet<>();

        try {
            missing.addAll(
                sourceAuthStrategy.get().getRequiredConfigProperties().stream()
                    .filter(configProperty -> config.getConfigPropertyAsOptional(configProperty).isEmpty())
                    .filter(configProperty -> secretStore.getConfigPropertyAsOptional(configProperty).isEmpty())
                    .map(ConfigService.ConfigProperty::name)
                    .collect(Collectors.toSet()));
        } catch (Throwable e) {
            rethrowIfConnectivityFailure(e);
            // will fail if sourceAuthStrategy is not set up properly
            logInDev(e.getMessage(), e);
            missing = new HashSet<>();
        }

        try {
            Optional<String> targetHost = apiModeConfig.getTargetHost();

            if (targetHost.isEmpty() || StringUtils.isBlank(targetHost.get())) {
                missing.add(ApiModeConfig.ApiModeConfigProperty.TARGET_HOST.name());
            } else if (targetHost.get().startsWith("http://")) {
                log.warning("TARGET_HOST must use https; http:// is not supported: "
                        + targetHost.get());
                missing.add(ApiModeConfig.ApiModeConfigProperty.TARGET_HOST.name());
            }
        } catch (Throwable ignored) {
            rethrowIfConnectivityFailure(ignored);
            logInDev("Failed to add TARGET_HOST info to health check", ignored);
        }

        HealthCheckResult.HealthCheckResultBuilder healthCheckResult = HealthCheckResult.builder()
                .javaSourceCodeVersion(ProxyConstants.JAVA_SOURCE_CODE_VERSION)
                .userAgent(proxyConstants.getUserAgent())
                .configuredSource(config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE).orElse(null))
                .configuredHost(apiModeConfig.getTargetHost().orElse(null))
                .nonDefaultSalt(secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT).isPresent())
                .pseudonymImplementation(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYM_IMPLEMENTATION).orElse(null))
                .missingConfigProperties(missing)
                .callerIp(request.getClientIp().orElse("unknown"));

        if (!apiModeConfig.getAllowedDataAccessIpBlocks().isEmpty()) {
            boolean clientIpAllowed = networkSecurityUtils.isDataAccessIpAllowed(
                    request.getClientIp().orElse(null));
            healthCheckResult.clientIpAuthorized(clientIpAllowed);
            if (!clientIpAllowed) {
                healthCheckResult.warningMessage(
                        "Client IP is not authorized to access this proxy instance.");
            }
        }

        try {
            config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYMIZE_APP_IDS)
                .map(Boolean::parseBoolean)
                .ifPresent(healthCheckResult::pseudonymizeAppIds);
        } catch (Throwable e) {
            rethrowIfConnectivityFailure(e);
            logInDev("Failed to add pseudonymizeAppIds to health check", e);
        }

        try {
            //collect toMap doesn't like null values; presumably people who see Unix-epoch will
            // recognize it means unknown/unknowable; in practice, won't be used due to filter atm
            final Instant PLACEHOLDER_FOR_NULL_LAST_MODIFIED = Instant.ofEpochMilli(0);
            healthCheckResult.configPropertiesLastModified(sourceAuthStrategy.get().getAllConfigProperties().stream()
                    .map(param -> {
                        Optional<ConfigService.ConfigValueWithMetadata> fromConfig = config.getConfigPropertyWithMetadata(param);
                        if (fromConfig.isEmpty()) {
                            fromConfig = secretStore.getConfigPropertyWithMetadata(param);
                        }

                        return Pair.of(param, fromConfig);
                    })
                    .filter(p -> p.getValue().isPresent()) // only values found
                    .filter(p -> p.getValue().get().getLastModifiedDate().isPresent()) // only values with last modified date, as others pointless
                    .collect(Collectors.toMap(p -> p.getKey().name(),
                            p -> p.getValue()
                                    .map(metadata -> metadata.getLastModifiedDate().orElse(PLACEHOLDER_FOR_NULL_LAST_MODIFIED))
                                .orElse(PLACEHOLDER_FOR_NULL_LAST_MODIFIED))));
        } catch (Throwable e) {
            rethrowIfConnectivityFailure(e);
            logInDev("Failed to fill 'configPropertiesLastModified' on health check", e);
        }

        try {
            apiModeConfig.getSourceAuthStrategyIdentifier()
                    .ifPresent(healthCheckResult::sourceAuthStrategy);
        } catch (Throwable e) {
            rethrowIfConnectivityFailure(e);
            logInDev("Failed to add sourceAuthStrategy to health check", e);
        }

        try {
            config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.GRANT_TYPE)
                    .ifPresent(healthCheckResult::sourceAuthGrantType);
        } catch (Throwable e) {
            rethrowIfConnectivityFailure(e);
            logInDev("Failed to add sourceAuthGrantType to health check", e);
        }

        try {
            config.getConfigPropertyAsOptional(ProxyConfigProperty.BUNDLE_FILENAME)
                    .ifPresent(healthCheckResult::bundleFilename);
        } catch (Throwable e) {
            rethrowIfConnectivityFailure(e);
            logInDev("Failed to add bundleFilename to health check", e);
        }

        try {
            rulesUtils.getRulesFromConfig(config, envVarsConfigService)
                    .ifPresent(rules -> healthCheckResult.rules(rulesUtils.asYaml(rules)));
        } catch (co.worklytics.psoxy.rules.InvalidRulesException e) {
            logInDev("Failed to add rules to health check: " + e.getMessage(), e);
            healthCheckResult.warningMessage("RULES configuration error: " + e.getErrorCause().name());
            try {
                config.getConfigPropertyAsOptional(ProxyConfigProperty.RULES)
                        .ifPresent(healthCheckResult::rules);
            } catch (Throwable ignored) {
                rethrowIfConnectivityFailure(ignored);
            }
        } catch (Throwable e) {
            rethrowIfConnectivityFailure(e);
            logInDev("Failed to add rules to health check", e);
        }

        // if SALT configured, as a hash of it to the health check, to enable detection of changes
        // (if salt changes, client needs to know; as all subsequent pseudonyms produced by proxy instance from that point
        // will be inconsistent with the prior ones)
        Optional.of(piiSaltHash())
            .filter(StringUtils::isNotBlank)
            .ifPresent(healthCheckResult::saltSha256Hash);

        try {
            sourceAuthStrategy.get().validateConfigValues().forEach(healthCheckResult::warningMessage);
        } catch (Throwable e) {
            rethrowIfConnectivityFailure(e);
            logInDev("Failed to add warnings from sourceAuthStrategy to health check", e);
        }

        HttpEventResponse.HttpEventResponseBuilder responseBuilder = HttpEventResponse.builder();

        try {
            HealthCheckResult result = healthCheckResult.build();
            responseBuilder.statusCode(responseStatusCode(result));
            responseBuilder.header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8).getMimeType());
            String json = objectMapper.writeValueAsString(result);
            responseBuilder.body(json + "\r\n");
            if (!result.passed()) {
                log.warning("Health check failed: " + json);
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to write health check details", e);
        }

        return responseBuilder.build();
    }

    /*
     * @return a SHA-256 hash of the salt, to aid in detecting changes to the salt value.
     */
    public String piiSaltHash() {
        if (piiSaltHash == null) {
            synchronized ($piiSaltHashLock) {
                if (piiSaltHash == null) {
                    piiSaltHash = secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                        .map(salt -> hashUtils.hash(salt, ProxyConstants.SALT_FOR_SALT)).orElse("");
                }
            }
        }
        return piiSaltHash;
    }


    private int responseStatusCode(@NonNull HealthCheckResult healthCheckResult) {
        if (healthCheckResult.passed()) {
            return HttpStatus.SC_OK;
        } else {
            return HttpStatus.SC_PRECONDITION_FAILED;
        }
    }

}
