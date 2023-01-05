package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.HealthCheckResult;
import co.worklytics.psoxy.gateway.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

        HealthCheckResult healthCheckResult = HealthCheckResult.builder()
            .configuredSource(config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE).orElse(null))
            .nonDefaultSalt(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT).isPresent())
            .missingConfigProperties(missing)
            .build();

        HttpEventResponse.HttpEventResponseBuilder responseBuilder = HttpEventResponse.builder();

        try {
            responseBuilder.statusCode(responseStatusCode(healthCheckResult));
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
