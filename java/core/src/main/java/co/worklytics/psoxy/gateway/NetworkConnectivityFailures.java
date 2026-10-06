package co.worklytics.psoxy.gateway;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import org.apache.commons.lang3.StringUtils;
import co.worklytics.psoxy.utils.LogSanitizationUtils;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Detects client-side failures to reach a service the proxy depends on (DNS, connect timeout,
 * connection refused), including calls to cloud APIs that are not on the public internet.
 *
 * <p>Stateless and safe to call from any thread. Cloud SDK types stay in the platform modules;
 * those modules translate their client errors into {@link ConfigStoreUnreachableException}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class NetworkConnectivityFailures {

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
     * Short description of the failure suitable for a response body. Prefers the message on
     * {@link ConfigStoreUnreachableException}, which platform code sets from the client error.
     */
    public static String describe(Throwable throwable) {
        String fromConfigStore = null;
        String deepest = null;
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConfigStoreUnreachableException
                    && StringUtils.isNotBlank(current.getMessage())) {
                fromConfigStore = current.getMessage();
            }
            String message = StringUtils.trimToNull(current.getMessage());
            if (message != null) {
                deepest = message;
            }
            current = current.getCause();
        }
        String chosen = fromConfigStore != null ? fromConfigStore : deepest;
        if (chosen == null && throwable != null) {
            chosen = throwable.getClass().getSimpleName();
        }
        if (chosen == null) {
            return "";
        }
        return StringUtils.abbreviate(LogSanitizationUtils.redactPotentialPii(chosen), 400);
    }

    private static boolean isConnectivityType(Throwable throwable) {
        if (throwable instanceof ConfigStoreUnreachableException
                || throwable instanceof UnknownHostException
                || throwable instanceof ConnectException
                || throwable instanceof NoRouteToHostException
                || throwable instanceof PortUnreachableException
                || throwable instanceof SocketTimeoutException
                || throwable instanceof UnresolvedAddressException) {
            return true;
        }
        String className = throwable.getClass().getName();
        return className.endsWith("ConnectTimeoutException")
                || className.endsWith("HttpConnectTimeoutException")
                || className.endsWith("UnknownHostException");
    }
}
