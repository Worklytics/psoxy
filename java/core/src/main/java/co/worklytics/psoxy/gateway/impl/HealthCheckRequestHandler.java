package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.HealthCheckResult;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.rules.RulesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Request handler that performs health check duties
 */
@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class HealthCheckRequestHandler {

    static final String JAVA_SOURCE_CODE_VERSION = "v0.4.60";

    @Inject
    EnvVarsConfigService envVarsConfigService;
    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;
    @Inject
    SourceAuthStrategy sourceAuthStrategy;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    RulesUtils rulesUtils;

    public Optional<HttpEventResponse> handleIfHealthCheck(HttpEventRequest request) {
        if (isHealthCheckRequest(request)) {
            return Optional.of(handle());
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

    private HttpEventResponse handle() {
        Set<String> missing =
                sourceAuthStrategy.getRequiredConfigProperties().stream()
                        .filter(configProperty -> config.getConfigPropertyAsOptional(configProperty).isEmpty())
                        .filter(configProperty -> secretStore.getConfigPropertyAsOptional(configProperty).isEmpty())
                        .map(ConfigService.ConfigProperty::name)
                        .collect(Collectors.toSet());

        try {
            Optional<String> targetHost = config.getConfigPropertyAsOptional(ProxyConfigProperty.TARGET_HOST);

            if (targetHost.isEmpty() || StringUtils.isBlank(targetHost.get())) {
                missing.add(ProxyConfigProperty.TARGET_HOST.name());
            }
        } catch (Throwable ignored) {
            logInDev("Failed to add TARGET_HOST info to health check", ignored);
        }

        HealthCheckResult.HealthCheckResultBuilder healthCheckResult = HealthCheckResult.builder()
                .javaSourceCodeVersion(JAVA_SOURCE_CODE_VERSION)
                .configuredSource(config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE).orElse(null))
                .configuredHost(config.getConfigPropertyAsOptional(ProxyConfigProperty.TARGET_HOST).orElse(null))
                .nonDefaultSalt(secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT).isPresent())
                .pseudonymImplementation(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYM_IMPLEMENTATION).orElse(null))
                .missingConfigProperties(missing);

        try {
            config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYMIZE_APP_IDS)
                .map(Boolean::parseBoolean)
                .ifPresent(healthCheckResult::pseudonymizeAppIds);
        } catch (Throwable e) {
            logInDev("Failed to add pseudonymizeAppIds to health check", e);
        }

        try {
            //collect toMap doesn't like null values; presumably people who see Unix-epoch will
            // recognize it means unknown/unknowable; in practice, won't be used due to filter atm
            final Instant PLACEHOLDER_FOR_NULL_LAST_MODIFIED = Instant.ofEpochMilli(0);
            healthCheckResult.configPropertiesLastModified(sourceAuthStrategy.getAllConfigProperties().stream()
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
            logInDev("Failed to fill 'configPropertiesLastModified' on health check", e);
        }

        try {
            config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER)
                    .ifPresent(healthCheckResult::sourceAuthStrategy);
        } catch (Throwable e) {
            logInDev("Failed to add sourceAuthStrategy to health check", e);
        }

        try {
            config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.GRANT_TYPE)
                    .ifPresent(healthCheckResult::sourceAuthGrantType);
        } catch (Throwable e) {
            logInDev("Failed to add sourceAuthGrantType to health check", e);
        }

        try {
            config.getConfigPropertyAsOptional(ProxyConfigProperty.BUNDLE_FILENAME)
                    .ifPresent(healthCheckResult::bundleFilename);
        } catch (Throwable e) {
            logInDev("Failed to add bundleFilename to health check", e);
        }

        try {
            rulesUtils.getRulesFromConfig(config, envVarsConfigService)
                    .ifPresent(rules -> healthCheckResult.rules(rulesUtils.asYaml(rules)));
        } catch (Throwable e) {
            logInDev("Failed to add rules to health check", e);
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

    private int responseStatusCode(@NonNull HealthCheckResult healthCheckResult) {
        if (healthCheckResult.passed()) {
            return HttpStatus.SC_OK;
        } else {
            return HttpStatus.SC_PRECONDITION_FAILED;
        }
    }

}
