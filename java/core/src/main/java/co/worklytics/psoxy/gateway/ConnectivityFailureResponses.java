package co.worklytics.psoxy.gateway;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import javax.inject.Inject;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.worklytics.psoxy.ErrorCauses;
import co.worklytics.psoxy.HealthCheckResult;
import co.worklytics.psoxy.ProcessedDataMetadataFields;
import lombok.AllArgsConstructor;
import lombok.extern.java.Log;

/**
 * Writes the 503 health-check body for a connectivity failure.
 *
 * <p>Uses the injected {@link ObjectMapper}. The client message comes from the platform parser
 * when it has one. Otherwise the body is a single sentence that says to check the logs. The
 * exception text is logged and is not copied into the response. Stateless aside from those
 * dependencies, and safe to call from any thread.
 */
@Log
@AllArgsConstructor(onConstructor_ = @Inject)
public class ConnectivityFailureResponses {

    private static final String CONFIG_STORE_FALLBACK =
            "Unable to reach the configuration store. Check the logs.";

    private static final String DEPENDENT_SERVICE_FALLBACK =
            "Unable to reach a dependent service. Check the logs.";

    private final ObjectMapper objectMapper;

    /**
     * @param duringConfigRead health check or container startup, which is reading configuration
     */
    public HttpEventResponse toResponse(String callerIp, DependencyConnectivityFailure failure,
            Throwable cause, boolean duringConfigRead) {
        ErrorCauses error = errorCause(failure, duringConfigRead);
        String clientMessage = clientMessage(failure, error);
        log.log(Level.SEVERE, clientMessage, cause);

        HealthCheckResult healthCheck = HealthCheckResult.builder()
                .javaSourceCodeVersion(ProxyConstants.JAVA_SOURCE_CODE_VERSION)
                .callerIp(callerIp)
                .nonDefaultSalt(false)
                .missingConfigProperties(Set.of())
                .error(error.name())
                .warningMessage(clientMessage)
                .build();

        HttpEventResponse.HttpEventResponseBuilder responseBuilder = HttpEventResponse.builder()
                .statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE)
                .header(HttpHeaders.CONTENT_TYPE,
                        ContentType.APPLICATION_JSON.withCharset(StandardCharsets.UTF_8).getMimeType())
                .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(), error.name());
        try {
            String healthCheckJson = objectMapper.writeValueAsString(healthCheck);
            responseBuilder.body(healthCheckJson + "\r\n");
            log.warning("Health check failed: " + healthCheckJson);
        } catch (IOException writeFailure) {
            log.log(Level.WARNING, "Failed to write connectivity health check details", writeFailure);
            responseBuilder.body("{\"error\":\"" + error.name() + "\"}\r\n");
        }
        return responseBuilder.build();
    }

    public Optional<HttpEventResponse> toResponse(String callerIp, Throwable cause,
            boolean duringConfigRead, ConnectivityFailureMatcher matcher) {
        return matcher.match(cause)
                .map(failure -> toResponse(callerIp, failure, cause, duringConfigRead));
    }

    public String callerIp(HttpEventRequest request) {
        try {
            if (request == null) {
                return "unknown";
            }
            return request.getClientIp().orElse("unknown");
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private String clientMessage(DependencyConnectivityFailure failure, ErrorCauses error) {
        if (failure.getClientMessage() != null) {
            return failure.getClientMessage();
        }
        if (error == ErrorCauses.CONFIG_STORE_UNREACHABLE) {
            return CONFIG_STORE_FALLBACK;
        }
        return DEPENDENT_SERVICE_FALLBACK;
    }

    private ErrorCauses errorCause(DependencyConnectivityFailure failure, boolean duringConfigRead) {
        if (failure.isConfigStore()) {
            return ErrorCauses.CONFIG_STORE_UNREACHABLE;
        }
        if (duringConfigRead && failure.getService() == null) {
            return ErrorCauses.CONFIG_STORE_UNREACHABLE;
        }
        return ErrorCauses.DEPENDENT_SERVICE_UNREACHABLE;
    }
}
