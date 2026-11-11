package co.worklytics.psoxy.gateway;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Locale;
import java.util.Optional;
import javax.inject.Inject;
import org.apache.commons.lang3.exception.ExceptionUtils;
import lombok.AllArgsConstructor;

/**
 * {@code java.net} and {@code java.nio} failures that mean a TCP connection never completed.
 *
 * <p>Stateless and safe to call from any thread.
 */
@AllArgsConstructor(onConstructor_ = @Inject)
public class JavaNetConnectivityFailures implements ConnectivityFailures {

    private final DependencyServiceNames serviceNames;

    @Override
    public Optional<DependencyConnectivityFailure> match(Throwable throwable) {
        boolean connectivity = false;
        String service = null;
        for (Throwable current : ExceptionUtils.getThrowableList(throwable)) {
            if (isConnectivityType(current)) {
                connectivity = true;
            }
            if (service == null) {
                service = serviceNames.findIn(current.getMessage()).orElse(null);
            }
        }
        if (!connectivity) {
            return Optional.empty();
        }
        return Optional.of(DependencyConnectivityFailure.builder().service(service).build());
    }

    /**
     * A {@link SocketTimeoutException} is a connection failure only when the message or the
     * concrete class says the TCP connect timed out. A read timeout after the connection is up
     * belongs to the source-API read-timeout path.
     */
    private static boolean isConnectivityType(Throwable throwable) {
        if (throwable instanceof SocketTimeoutException socketTimeout) {
            if (isConnectTimeoutClass(socketTimeout)) {
                return true;
            }
            String message = socketTimeout.getMessage();
            return message != null && message.toLowerCase(Locale.ROOT).contains("connect");
        }
        if (throwable instanceof UnknownHostException
                || throwable instanceof ConnectException
                || throwable instanceof NoRouteToHostException
                || throwable instanceof PortUnreachableException
                || throwable instanceof UnresolvedAddressException) {
            return true;
        }
        return isConnectTimeoutClass(throwable) || throwable.getClass().getName().endsWith("UnknownHostException");
    }

    private static boolean isConnectTimeoutClass(Throwable throwable) {
        String className = throwable.getClass().getName();
        return className.endsWith("ConnectTimeoutException")
                || className.endsWith("HttpConnectTimeoutException");
    }
}
