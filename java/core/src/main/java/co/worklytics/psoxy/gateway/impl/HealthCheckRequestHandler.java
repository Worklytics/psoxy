package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.HashUtils;
import co.worklytics.psoxy.HealthCheckResult;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.rules.RulesUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Lazy;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
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

    public static final String JAVA_SOURCE_CODE_VERSION = "v0.5.3";

    /**
     * a random UUID used to salt the hash of the salt.  Purpose of this is to invalidate any non-purpose built rainbow table solution.
     *   (Eg, if we just directly hashed the salt, a general rainbow table of hashes could be used to determine the salt value)
     *
     *  That said, if salt is 20+ random characters, there is no *general* rainbow table of that length in existence and one is impossible to
     *  build, as storing it requires ~10e25 petabytes - which is about 10e20 more storage than humanity actually has. So this additional
     *  protection isn't so necessary, but whatever.
     *
     *  do NOT change this value. if you do, we won't be able to detect that proxy-side salts of changed.
     */
    private static final String SALT_FOR_SALT = "f33c366c-ae91-4819-b221-f9794ebb8145";

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

    String piiSaltHash;

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

    private HttpEventResponse handle(HttpEventRequest request) {
        Set<String> missing;

        try {
            missing =
                sourceAuthStrategy.get().getRequiredConfigProperties().stream()
                    .filter(configProperty -> config.getConfigPropertyAsOptional(configProperty).isEmpty())
                    .filter(configProperty -> secretStore.getConfigPropertyAsOptional(configProperty).isEmpty())
                    .map(ConfigService.ConfigProperty::name)
                    .collect(Collectors.toSet());
        } catch (Throwable e) {
            // will fail if sourceAuthStrategy is not set up properly
            logInDev(e.getMessage(), e);
            missing = Collections.emptySet();
        }

        try {
            Optional<String> targetHost = config.getConfigPropertyAsOptional(ApiModeConfigProperty.TARGET_HOST);

            if (targetHost.isEmpty() || StringUtils.isBlank(targetHost.get())) {
                missing.add(ApiModeConfigProperty.TARGET_HOST.name());
            }
        } catch (Throwable ignored) {
            logInDev("Failed to add TARGET_HOST info to health check", ignored);
        }

        HealthCheckResult.HealthCheckResultBuilder healthCheckResult = HealthCheckResult.builder()
                .javaSourceCodeVersion(JAVA_SOURCE_CODE_VERSION)
                .configuredSource(config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE).orElse(null))
                .configuredHost(config.getConfigPropertyAsOptional(ApiModeConfigProperty.TARGET_HOST).orElse(null))
                .nonDefaultSalt(secretStore.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT).isPresent())
                .pseudonymImplementation(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSEUDONYM_IMPLEMENTATION).orElse(null))
                .missingConfigProperties(missing)
                .callerIp(request.getClientIp().orElse("unknown"));


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
            logInDev("Failed to fill 'configPropertiesLastModified' on health check", e);
        }

        try {
            config.getConfigPropertyAsOptional(ApiModeConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER)
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

        // if SALT configured, as a hash of it to the health check, to enable detection of changes
        // (if salt changes, client needs to know; as all subsequent pseudonyms produced by proxy instance from that point
        // will be inconsistent with the prior ones)
        config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                .ifPresent(salt -> healthCheckResult.saltSha256Hash(hashUtils.hash(salt, SALT_FOR_SALT)));

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
            piiSaltHash = config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                .map(salt -> hashUtils.hash(salt, SALT_FOR_SALT)).orElse("");
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
