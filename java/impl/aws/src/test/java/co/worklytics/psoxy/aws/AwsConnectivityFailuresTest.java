package co.worklytics.psoxy.aws;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import co.worklytics.psoxy.gateway.ConnectivityFailureMatcher;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import co.worklytics.psoxy.gateway.DependencyServiceNames;
import co.worklytics.psoxy.gateway.JavaNetConnectivityFailures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AwsConnectivityFailuresTest {

    private final DependencyServiceNames names = new DependencyServiceNames();
    private final AwsConnectivityFailures aws = new AwsConnectivityFailures(names);
    private final ConnectivityFailureMatcher matcher = new ConnectivityFailureMatcher(Set.of(
            new JavaNetConnectivityFailures(names),
            aws));

    @Test
    void sdkHttpFailureNamesTheParsedService() {
        SdkClientException http = SdkClientException.builder()
                .message("Unable to execute HTTP request: Connect to ssm.us-east-1.amazonaws.com:443 failed")
                .cause(new java.net.SocketTimeoutException("connect timed out"))
                .build();

        Optional<DependencyConnectivityFailure> match = matcher.match(http);

        assertTrue(match.isPresent());
        assertEquals("ssm", match.get().getService());
        assertEquals("Unable to reach SSM Parameter Store. Check the logs.", match.get().getClientMessage());
        assertTrue(aws.match(http).isPresent());
    }

    @Test
    void credentialFailureIsNotConnectivity() {
        SdkClientException credentials = SdkClientException.builder()
                .message("Unable to load credentials from any of the providers in the chain")
                .build();

        assertFalse(aws.match(credentials).isPresent());
        assertFalse(matcher.match(credentials).isPresent());
    }
}
