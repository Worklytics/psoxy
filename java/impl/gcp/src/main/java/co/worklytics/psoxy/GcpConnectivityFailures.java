package co.worklytics.psoxy;

import java.util.Optional;
import javax.inject.Inject;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import co.worklytics.psoxy.gateway.ConnectivityFailures;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import co.worklytics.psoxy.gateway.DependencyServiceNames;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.AllArgsConstructor;

/**
 * Google client failures that mean the call never completed.
 *
 * <p>{@code UNAVAILABLE} and {@code DEADLINE_EXCEEDED} are the statuses a client raises when DNS
 * or TCP fails. Permission denied and not-found are not connectivity problems. Stateless and safe
 * to call from any thread. {@code java.net} failures are recognized by
 * {@link co.worklytics.psoxy.gateway.JavaNetConnectivityFailures}, which is bound beside this one.
 */
@AllArgsConstructor(onConstructor_ = @Inject)
public class GcpConnectivityFailures implements ConnectivityFailures {

    private final DependencyServiceNames serviceNames;

    @Override
    public Optional<DependencyConnectivityFailure> match(Throwable throwable) {
        String service = null;
        boolean transport = false;
        Throwable current = throwable;
        while (current != null) {
            if (isTransportStatus(current)) {
                transport = true;
            }
            if (service == null) {
                service = serviceNames.findIn(current.getMessage()).orElse(null);
            }
            current = current.getCause();
        }
        if (!transport) {
            return Optional.empty();
        }
        return Optional.of(DependencyConnectivityFailure.builder()
                .service(service)
                .clientMessage(clientMessage(service))
                .build());
    }

    private String clientMessage(String service) {
        if ("secretmanager".equals(service)) {
            return "Unable to reach Secret Manager. Check the logs.";
        }
        if ("kms".equals(service)) {
            return "Unable to reach Cloud KMS. Check the logs.";
        }
        if ("gcs".equals(service)) {
            return "Unable to reach Cloud Storage. Check the logs.";
        }
        if ("pubsub".equals(service)) {
            return "Unable to reach Pub/Sub. Check the logs.";
        }
        if ("jwks".equals(service)) {
            return "Unable to reach a JWKS endpoint. Check the logs.";
        }
        return "Unable to reach a Google API. Check the logs.";
    }

    private static boolean isTransportStatus(Throwable throwable) {
        if (throwable instanceof ApiException apiException && apiException.getStatusCode() != null) {
            StatusCode.Code code = apiException.getStatusCode().getCode();
            return code == StatusCode.Code.UNAVAILABLE || code == StatusCode.Code.DEADLINE_EXCEEDED;
        }
        if (throwable instanceof StatusRuntimeException grpc) {
            Status.Code code = grpc.getStatus().getCode();
            return code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED;
        }
        return false;
    }
}
