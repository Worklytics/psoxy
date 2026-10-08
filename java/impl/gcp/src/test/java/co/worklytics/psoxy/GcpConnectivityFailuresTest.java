package co.worklytics.psoxy;

import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.api.gax.rpc.StatusCode;
import com.google.api.gax.rpc.UnavailableException;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.junit.jupiter.api.Test;
import co.worklytics.psoxy.gateway.ConnectivityFailureMatcher;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import co.worklytics.psoxy.gateway.DependencyServiceNames;
import co.worklytics.psoxy.gateway.JavaNetConnectivityFailures;

import static org.mockito.Mockito.mock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GcpConnectivityFailuresTest {

    private final DependencyServiceNames names = new DependencyServiceNames();
    private final ConnectivityFailureMatcher matcher = new ConnectivityFailureMatcher(Set.of(
            new JavaNetConnectivityFailures(names),
            new GcpConnectivityFailures(names)));

    @Test
    void unavailableSecretManagerCallNamesTheParsedService() {
        Throwable failure = new UncheckedExecutionException(unavailable("secretmanager.googleapis.com"));

        Optional<DependencyConnectivityFailure> match = matcher.match(failure);

        assertTrue(match.isPresent());
        assertEquals("secretmanager", match.get().getService());
        assertEquals("Unable to reach Secret Manager. Check the logs.", match.get().getClientMessage());
        assertTrue(match.get().isConfigStore());
    }

    @Test
    void permissionDeniedIsNotATransportFailure() {
        PermissionDeniedException denied = new PermissionDeniedException(
                "Permission 'secretmanager.versions.access' denied",
                null,
                status(StatusCode.Code.PERMISSION_DENIED),
                false);

        assertFalse(matcher.match(denied).isPresent());
    }

    @Test
    void unknownHostWithoutAKnownServiceHasNoToken() {
        Optional<DependencyConnectivityFailure> match = matcher.match(
                new RuntimeException(new UnknownHostException("www.googleapis.com")));

        assertTrue(match.isPresent());
        assertNull(match.get().getService());
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
        assertEquals("secretmanager", startup.match(startup.failure()).orElseThrow().getService());
    }

    @Test
    void startupRetriesAfterTheCooldown() {
        Clock start = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        GcpContainerStartup startup = new GcpContainerStartup();
        startup.setClock(start);
        assertNull(startup.getOrCreate(() -> {
            throw unavailable("secretmanager.googleapis.com");
        }));

        startup.setClock(Clock.fixed(start.instant().plusSeconds(61), ZoneOffset.UTC));
        GcpContainer created = mockContainer();
        assertEquals(created, startup.getOrCreate(() -> created));
        assertFalse(startup.failed());
    }

    @Test
    void startupRethrowsFailuresThatAreNotTransport() {
        GcpContainerStartup startup = new GcpContainerStartup();

        assertThrows(IllegalStateException.class, () -> startup.getOrCreate(() -> {
            throw new IllegalStateException("bug");
        }));
        assertFalse(startup.failed());
    }

    private static GcpContainer mockContainer() {
        return mock(GcpContainer.class);
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
