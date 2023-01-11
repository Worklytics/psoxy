package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.HealthCheckResult;
import co.worklytics.psoxy.ResponseHeader;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;
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
import java.util.stream.Stream;

/**
 * Request handler that performs health check duties
 */
@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class HealthCheckRequestHandler {

    @Inject
    ConfigService config;
    @Inject
    SourceAuthStrategy sourceAuthStrategy;
    @Inject
    ObjectMapper objectMapper;

    private boolean isHealthCheckRequest(HttpEventRequest request) {
        return request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()).isPresent();
    }

    public Optional<HttpEventResponse> handleIfHealthCheck(HttpEventRequest request) {
        if (isHealthCheckRequest(request)) {
            return Optional.of(handle());
        }
        return Optional.empty();
    }

    private HttpEventResponse handle() {
        Set<String> missing =
            sourceAuthStrategy.getRequiredConfigProperties().stream()
                .filter(configProperty -> config.getConfigPropertyAsOptional(configProperty).isEmpty())
                .map(ConfigService.ConfigProperty::name)
                .collect(Collectors.toSet());

        HealthCheckResult.HealthCheckResultBuilder healthCheckResult = HealthCheckResult.builder()
            .configuredSource(config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE).orElse(null))
            .nonDefaultSalt(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT).isPresent())
            .missingConfigProperties(missing);

        try {
            healthCheckResult.configPropertiesLastModified(sourceAuthStrategy.getAllConfigProperties().stream()
                .map(param -> Pair.of(param, config.getConfigPropertyWithMetadata(param)))
                .collect(Collectors.toMap(p -> p.getKey().name(),
                    p -> p.getValue()
                        .map(metadata -> metadata.getLastModifiedDate().orElse(null))
                        .orElse(null))));

            config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER)
                .ifPresent(healthCheckResult::sourceAuthStrategy);

            config.getConfigPropertyAsOptional(OAuthRefreshTokenSourceAuthStrategy.ConfigProperty.GRANT_TYPE)
                .ifPresent(healthCheckResult::sourceAuthGrantType);
        } catch (Throwable e) {
            log.log(Level.WARNING, "Failed to add config debug info to health check");
        }

        HttpEventResponse.HttpEventResponseBuilder responseBuilder = HttpEventResponse.builder();

        try {
            responseBuilder.statusCode(responseStatusCode(healthCheckResult.build()));
            responseBuilder.header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8).getMimeType());
            responseBuilder.body(objectMapper.writeValueAsString(healthCheckResult) + "\r\n");
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to write health check details", e);
        }

        return responseBuilder.build();
    }

    private int responseStatusCode(@NonNull HealthCheckResult healthCheckResult) {
        if (healthCheckResult.passed()) {
            return HttpStatus.SC_OK;
        } else {
            return 512;
        }
    }

}
