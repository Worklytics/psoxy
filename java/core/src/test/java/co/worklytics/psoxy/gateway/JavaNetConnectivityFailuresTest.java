package co.worklytics.psoxy.gateway;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.Optional;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaNetConnectivityFailuresTest {

    private final JavaNetConnectivityFailures failures =
            new JavaNetConnectivityFailures(new DependencyServiceNames());

    @Test
    void detectsWrappedDnsFailureAndKeepsOnlyTheServiceToken() {
        Throwable failure = new UncheckedExecutionException(
                new RuntimeException(new UnknownHostException("ssm.us-east-1.amazonaws.com")));

        Optional<DependencyConnectivityFailure> match = failures.match(failure);

        assertTrue(match.isPresent());
        assertEquals("ssm", match.get().getService());
        assertTrue(match.get().isConfigStore());
    }

    @Test
    void connectExceptionWithoutAKnownHostHasNoServiceToken() {
        Throwable failure = new RuntimeException("read salt", new ConnectException("Connection refused"));

        Optional<DependencyConnectivityFailure> match = failures.match(failure);

        assertTrue(match.isPresent());
        assertEquals(null, match.get().getService());
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertFalse(failures.match(new IllegalStateException("bug")).isPresent());
    }

    @Test
    void ignoresCredentialMessagesThatAreNotANetworkFailure() {
        assertFalse(failures.match(new IllegalStateException(
                "Unable to load credentials from any of the providers in the chain")).isPresent());
    }

    @Test
    void parsesKnownServicesAndDropsUnknownHosts() {
        DependencyServiceNames names = new DependencyServiceNames();

        assertEquals(Optional.of("secretsmanager"),
                names.findIn("Connect to secretsmanager.us-east-1.amazonaws.com:443 failed"));
        assertEquals(Optional.of("secretmanager"),
                names.findIn("Unable to resolve host secretmanager.googleapis.com"));
        assertEquals(Optional.of("kms"), names.findIn("kms.us-east-1.amazonaws.com"));
        assertEquals(Optional.of("jwks"), names.findIn("https://accounts.google.com/oauth2/v3/certs/jwks"));
        assertEquals(Optional.of("s3"), names.findIn("bucket.s3.us-east-1.amazonaws.com"));
        assertEquals(Optional.empty(), names.findIn("Unable to resolve host www.googleapis.com"));
        assertEquals(Optional.empty(), names.findIn("user@example.com could not connect"));
    }
}
