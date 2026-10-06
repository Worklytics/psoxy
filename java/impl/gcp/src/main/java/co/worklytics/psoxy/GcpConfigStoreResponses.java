package co.worklytics.psoxy;

import java.io.IOException;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import co.worklytics.psoxy.gateway.impl.HealthCheckRequestHandler;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Writes the shared configuration-store health-check response onto a Cloud Functions HTTP response.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GcpConfigStoreResponses {

    static void write(HttpRequest request, HttpResponse response, Throwable failure) throws IOException {
        String callerIp = "unknown";
        if (request != null) {
            try {
                callerIp = CloudFunctionRequest.of(request).getClientIp().orElse("unknown");
            } catch (RuntimeException e) {
                callerIp = "unknown";
            }
        }
        GcpApiDataRequestHandler.fillGcpResponseFromGenericResponse(response,
                HealthCheckRequestHandler.configStoreUnreachable(
                        callerIp, GcpClientConnectivity.forHealthCheck(failure)));
    }
}
