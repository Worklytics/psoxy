package co.worklytics.psoxy;

import org.apache.commons.lang3.StringUtils;
import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import co.worklytics.psoxy.gateway.ConfigStoreUnreachableException;
import co.worklytics.psoxy.gateway.NetworkConnectivityFailures;
import co.worklytics.psoxy.utils.LogSanitizationUtils;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Google client failures that mean the Cloud Function could not complete a call to a Google API
 * such as Secret Manager.
 *
 * <p>{@code UNAVAILABLE} and {@code DEADLINE_EXCEEDED} are the statuses the Secret Manager client
 * raises when DNS or TCP to {@code secretmanager.googleapis.com} fails. That happens when VPC
 * egress is set to all traffic and the subnet has neither Private Google Access nor Cloud NAT.
 * Permission denied and not-found are not transport failures.
 *
 * <p>Stateless and safe to call from any thread.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GcpClientConnectivity {

    private static final String SECRET_MANAGER_HOST = "secretmanager.googleapis.com";

    public static boolean isTransportFailure(Throwable throwable) {
        if (NetworkConnectivityFailures.isConnectivityFailure(throwable)) {
            return true;
        }
        Throwable current = throwable;
        while (current != null) {
            if (isTransportStatus(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Core health checks quote {@link ConfigStoreUnreachableException}. Translate a raw Google
     * client failure into that type so the response still includes the client error text.
     */
    public static Throwable forHealthCheck(Throwable failure) {
        if (failure == null
                || failure instanceof ConfigStoreUnreachableException
                || !isTransportFailure(failure)) {
            return failure;
        }
        return new ConfigStoreUnreachableException(preferredMessage(failure), failure);
    }

    public static String describe(Throwable throwable) {
        String message = preferredMessage(throwable);
        if (message == null) {
            return NetworkConnectivityFailures.describe(throwable);
        }
        return StringUtils.abbreviate(LogSanitizationUtils.redactPotentialPii(message), 400);
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

    private static String preferredMessage(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String first = null;
        Throwable current = throwable;
        while (current != null) {
            String message = StringUtils.trimToNull(current.getMessage());
            if (message != null) {
                if (first == null) {
                    first = message;
                }
                if (message.contains(SECRET_MANAGER_HOST)) {
                    return message;
                }
            }
            current = current.getCause();
        }
        return first;
    }
}
