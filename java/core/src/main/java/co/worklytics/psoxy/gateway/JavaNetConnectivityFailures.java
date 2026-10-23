package co.worklytics.psoxy.gateway;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Optional;
import javax.inject.Inject;
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
        Throwable current = throwable;
        while (current != null) {
            if (isConnectivityType(current)) {
                connectivity = true;
            }
            if (service == null) {
                service = serviceNames.findIn(current.getMessage()).orElse(null);
            }
            current = current.getCause();
        }
        if (!connectivity) {
            return Optional.empty();
        }
        return Optional.of(DependencyConnectivityFailure.builder().service(service).build());
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
        return className.endsWith("ConnectTimeoutException")
                || className.endsWith("HttpConnectTimeoutException")
                || className.endsWith("UnknownHostException");
    }
}
