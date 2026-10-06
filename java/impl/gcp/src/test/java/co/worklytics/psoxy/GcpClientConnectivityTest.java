package co.worklytics.psoxy;

import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnavailableException;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcpClientConnectivityTest {

    @Test
    void unavailableSecretManagerCallIsATransportFailure() {
        Throwable failure = new UncheckedExecutionException(unavailable("secretmanager.googleapis.com"));

        assertTrue(GcpClientConnectivity.isTransportFailure(failure));
        assertTrue(GcpClientConnectivity.describe(failure).contains("secretmanager.googleapis.com"));
    }

    @Test
    void permissionDeniedIsNotATransportFailure() {
        PermissionDeniedException denied = new PermissionDeniedException(
                "Permission 'secretmanager.versions.access' denied",
                null,
                status(StatusCode.Code.PERMISSION_DENIED),
                false);

        assertFalse(GcpClientConnectivity.isTransportFailure(denied));
    }

    @Test
    void javaNetFailureIsATransportFailure() {
        assertTrue(GcpClientConnectivity.isTransportFailure(
                new RuntimeException(new UnknownHostException("secretmanager.googleapis.com"))));
    }

    @Test
    void startupRemembersSecretManagerFailureAndDoesNotRetry() {
        GcpContainerStartup startup = new GcpContainerStartup();
        AtomicInteger attempts = new AtomicInteger();

        GcpContainer created = startup.getOrCreate(() -> {
            attempts.incrementAndGet();
            throw unavailable("secretmanager.googleapis.com");
        });

        assertNull(created);
        assertTrue(startup.failed());
        assertNull(startup.getOrCreate(() -> {
            attempts.incrementAndGet();
            return null;
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void startupRethrowsFailuresThatAreNotTransport() {
        GcpContainerStartup startup = new GcpContainerStartup();

        assertThrows(IllegalStateException.class, () -> startup.getOrCreate(() -> {
            throw new IllegalStateException("bug");
        }));
        assertFalse(startup.failed());
    }

    private static UnavailableException unavailable(String host) {
        return new UnavailableException(
                "io.grpc.StatusRuntimeException: UNAVAILABLE: Unable to resolve host " + host,
                new UnknownHostException(host),
                status(StatusCode.Code.UNAVAILABLE),
                true);
    }

    private static StatusCode status(StatusCode.Code code) {
        return new StatusCode() {
            @Override
            public Code getCode() {
                return code;
            }

            @Override
            public Object getTransportCode() {
                return null;
            }
        };
    }
}
