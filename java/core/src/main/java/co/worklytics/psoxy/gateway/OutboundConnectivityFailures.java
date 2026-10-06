package co.worklytics.psoxy.gateway;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import org.apache.commons.lang3.StringUtils;
import co.worklytics.psoxy.utils.LogSanitizationUtils;

/**
 * Detects client-side failures to reach a remote service (DNS, connect timeout, connection
 * refused). Used to tell a blocked path to AWS SSM Parameter Store or Secrets Manager apart from
 * an application bug.
 *
 * <p>Stateless and safe to call from any thread.
 *
 * <p>AWS SDK types are matched by class name so this stays in the cloud-agnostic module.
 */
public final class OutboundConnectivityFailures {

    private static final String AWS_SDK_CLIENT_EXCEPTION =
            "software.amazon.awssdk.core.exception.SdkClientException";

    private static final String AWS_SDK_HTTP_FAILURE = "Unable to execute HTTP request";

    private OutboundConnectivityFailures() {
    }

    public static boolean isConnectivityFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (isConnectivityType(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /**
     * Short description of the failure suitable for a response body. Prefers the AWS SDK
     * "Unable to execute HTTP request" message, which is what CloudWatch logs for SSM.
     */
    public static String describe(Throwable throwable) {
        String httpFailure = null;
        String deepest = null;
        Throwable current = throwable;
        while (current != null) {
            String message = StringUtils.trimToNull(current.getMessage());
            if (message != null) {
                deepest = message;
                if (message.contains(AWS_SDK_HTTP_FAILURE)) {
                    httpFailure = message;
                }
            }
            current = current.getCause();
        }
        String chosen = httpFailure != null ? httpFailure : deepest;
        if (chosen == null) {
            chosen = throwable.getClass().getSimpleName();
        }
        return StringUtils.abbreviate(LogSanitizationUtils.redactPotentialPii(chosen), 400);
    }

    private static boolean isConnectivityType(Throwable throwable) {
        if (throwable instanceof UnknownHostException
                || throwable instanceof ConnectException
                || throwable instanceof NoRouteToHostException
                || throwable instanceof PortUnreachableException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof UnresolvedAddressException) {
            return true;
        }
        String className = throwable.getClass().getName();
        if (className.endsWith("ConnectTimeoutException")
                || className.endsWith("HttpConnectTimeoutException")
                || className.endsWith("UnknownHostException")) {
            return true;
        }
        // Credential-chain failures are also SdkClientException, and are not a network problem.
        if (AWS_SDK_CLIENT_EXCEPTION.equals(className)) {
            String message = throwable.getMessage();
            return message != null && message.contains(AWS_SDK_HTTP_FAILURE);
        }
        return false;
    }
}
