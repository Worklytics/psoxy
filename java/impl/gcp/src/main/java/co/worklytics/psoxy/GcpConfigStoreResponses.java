package co.worklytics.psoxy;

import java.io.IOException;
import javax.inject.Inject;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import co.worklytics.psoxy.gateway.ConnectivityFailureResponses;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import lombok.AllArgsConstructor;

/**
 * Copies a connectivity health-check response onto a Cloud Functions HTTP response.
 *
 * <p>Request handling receives this from Dagger. {@link GcpContainerStartup} constructs its own
 * instance, because that path runs when the graph did not start.
 */
@AllArgsConstructor(onConstructor_ = @Inject)
public class GcpConfigStoreResponses {

    private final ConnectivityFailureResponses responses;

    public void write(HttpRequest request, HttpResponse response, DependencyConnectivityFailure failure,
            Throwable cause, boolean duringConfigRead, int clientIpXForwardedForOffset) throws IOException {
        GcpApiDataRequestHandler.fillGcpResponseFromGenericResponse(response,
                responses.toResponse(callerIp(request, clientIpXForwardedForOffset), failure, cause, duringConfigRead));
    }

    private String callerIp(HttpRequest request, int clientIpXForwardedForOffset) {
        if (request == null) {
            return "unknown";
        }
        try {
            return CloudFunctionRequest.of(request, clientIpXForwardedForOffset)
                    .getClientIp().orElse("unknown");
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }
}
