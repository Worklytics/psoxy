package co.worklytics.psoxy.gateway;

import java.net.ConnectException;
import java.net.UnknownHostException;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkConnectivityFailuresTest {

    @Test
    void detectsWrappedDnsFailure() {
        Throwable failure = new UncheckedExecutionException(
                new RuntimeException(new UnknownHostException("ssm.us-east-1.amazonaws.com")));

        assertTrue(NetworkConnectivityFailures.isConnectivityFailure(failure));
        assertEquals("ssm.us-east-1.amazonaws.com", NetworkConnectivityFailures.describe(failure));
    }

    @Test
    void detectsConnectException() {
        Throwable failure = new RuntimeException("read salt", new ConnectException("Connection refused"));

        assertTrue(NetworkConnectivityFailures.isConnectivityFailure(failure));
        assertEquals("Connection refused", NetworkConnectivityFailures.describe(failure));
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertFalse(NetworkConnectivityFailures.isConnectivityFailure(new IllegalStateException("bug")));
    }

    @Test
    void prefersConfigStoreUnreachableMessage() {
        Throwable failure = new ConfigStoreUnreachableException(
                "Unable to execute HTTP request: Connect to ssm.us-east-1.amazonaws.com:443 failed: connect timed out",
                new java.net.SocketTimeoutException("connect timed out"));

        assertTrue(NetworkConnectivityFailures.isConnectivityFailure(failure));
        assertTrue(NetworkConnectivityFailures.describe(failure).contains("Unable to execute HTTP request"));
    }

    @Test
    void ignoresCredentialMessagesThatAreNotANetworkFailure() {
        assertFalse(NetworkConnectivityFailures.isConnectivityFailure(
                new IllegalStateException("Unable to load credentials from any of the providers in the chain")));
    }
}
