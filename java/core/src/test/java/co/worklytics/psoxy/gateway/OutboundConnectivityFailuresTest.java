package co.worklytics.psoxy.gateway;

import java.net.ConnectException;
import java.net.UnknownHostException;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundConnectivityFailuresTest {

    @Test
    void detectsWrappedDnsFailure() {
        Throwable failure = new UncheckedExecutionException(
                new RuntimeException(new UnknownHostException("ssm.us-east-1.amazonaws.com")));

        assertTrue(OutboundConnectivityFailures.isConnectivityFailure(failure));
        assertEquals("ssm.us-east-1.amazonaws.com", OutboundConnectivityFailures.describe(failure));
    }

    @Test
    void detectsConnectException() {
        Throwable failure = new RuntimeException("read salt", new ConnectException("Connection refused"));

        assertTrue(OutboundConnectivityFailures.isConnectivityFailure(failure));
        assertEquals("Connection refused", OutboundConnectivityFailures.describe(failure));
    }

    @Test
    void ignoresUnrelatedFailures() {
        assertFalse(OutboundConnectivityFailures.isConnectivityFailure(new IllegalStateException("bug")));
    }

    @Test
    void prefersAwsHttpRequestMessage() {
        Throwable failure = new AwsSdkHttpFailure(
                "Unable to execute HTTP request: Connect to ssm.us-east-1.amazonaws.com:443 failed: connect timed out",
                new java.net.SocketTimeoutException("connect timed out"));

        assertTrue(OutboundConnectivityFailures.isConnectivityFailure(failure));
        assertTrue(OutboundConnectivityFailures.describe(failure).contains("Unable to execute HTTP request"));
    }

    @Test
    void ignoresCredentialMessagesThatAreNotANetworkFailure() {
        assertFalse(OutboundConnectivityFailures.isConnectivityFailure(
                new IllegalStateException("Unable to load credentials from any of the providers in the chain")));
    }

    /**
     * Stand-in whose message matches the AWS SDK HTTP failure text. Connectivity is detected from
     * the nested socket timeout; {@link OutboundConnectivityFailures#describe} prefers the HTTP text.
     */
    public static class AwsSdkHttpFailure extends RuntimeException {
        public AwsSdkHttpFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
