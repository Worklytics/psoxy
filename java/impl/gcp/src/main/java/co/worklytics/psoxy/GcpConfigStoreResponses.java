package co.worklytics.psoxy;

import java.io.IOException;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import co.worklytics.psoxy.gateway.ConnectivityFailureResponses;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Copies a connectivity health-check response onto a Cloud Functions HTTP response.
 *
 * <p>The entrypoints are not injected. The response body itself comes from
 * {@link ConnectivityFailureResponses}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GcpConfigStoreResponses {

    static void write(HttpRequest request, HttpResponse response, ConnectivityFailureResponses responses,
            DependencyConnectivityFailure failure, Throwable cause, boolean duringConfigRead) throws IOException {
        GcpApiDataRequestHandler.fillGcpResponseFromGenericResponse(response,
                responses.toResponse(callerIp(request), failure, cause, duringConfigRead));
    }

    private static String callerIp(HttpRequest request) {
        if (request == null) {
            return "unknown";
        }
        try {
            return CloudFunctionRequest.of(request).getClientIp().orElse("unknown");
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }
}
