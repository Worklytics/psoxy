package co.worklytics.psoxy.aws;

import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import co.worklytics.psoxy.ErrorCauses;
import co.worklytics.psoxy.ProcessedDataMetadataFields;
import co.worklytics.psoxy.gateway.HttpEventResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaContainerStartupTest {

    @BeforeEach
    void setUp() {
        LambdaContainerStartup.reset();
    }

    @AfterEach
    void tearDown() {
        LambdaContainerStartup.reset();
    }

    @Test
    void initialize_recordsSsmConnectivityFailureForLaterHealthChecks() {
        LambdaContainerStartup.initialize(() -> {
            throw SdkClientException.builder()
                    .message("Unable to execute HTTP request: Connect to ssm.us-east-1.amazonaws.com:443 failed: connect timed out")
                    .cause(new java.net.SocketTimeoutException("connect timed out"))
                    .build();
        });

        assertTrue(LambdaContainerStartup.failed());
        HttpEventResponse response = LambdaContainerStartup.response("203.0.113.5");
        assertEquals(503, response.getStatusCode());
        assertEquals(ErrorCauses.CONFIG_STORE_UNREACHABLE.name(),
                response.getHeaders().get(ProcessedDataMetadataFields.ERROR.getHttpHeader()));
        assertTrue(response.getBody().contains("\"error\" : \"" + ErrorCauses.CONFIG_STORE_UNREACHABLE.name() + "\"")
                || response.getBody().contains("\"error\":\"" + ErrorCauses.CONFIG_STORE_UNREACHABLE.name() + "\""));
        assertTrue(response.getBody().contains("203.0.113.5"));
        assertTrue(response.getBody().contains("Unable to reach SSM Parameter Store. Check the logs."));
        assertFalse(response.getBody().contains("Unable to execute HTTP request"));
        assertFalse(response.getBody().contains("ssm.us-east-1.amazonaws.com"));
        assertFalse(response.getBody().contains("On AWS"));
    }

    @Test
    void initialize_rethrowsFailuresThatAreNotConnectivity() {
        assertThrows(IllegalStateException.class, () -> LambdaContainerStartup.initialize(() -> {
            throw new IllegalStateException("bug");
        }));
        assertFalse(LambdaContainerStartup.failed());
    }

    @Test
    void writeIfStartupFailed_writesJsonForApiGatewayV2HealthCheck() throws Exception {
        LambdaContainerStartup.initialize(() -> {
            throw new RuntimeException(new UnknownHostException("ssm.us-east-1.amazonaws.com"));
        });

        String event = """
                {
                  "version": "2.0",
                  "rawPath": "/",
                  "requestContext": {
                    "http": { "method": "GET", "sourceIp": "198.51.100.8" }
                  },
                  "headers": { "x-psoxy-health-check": "true" }
                }
                """;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        boolean written = LambdaContainerStartup.writeIfStartupFailed(
                new java.io.ByteArrayInputStream(event.getBytes(StandardCharsets.UTF_8)), output);

        assertTrue(written);
        String body = output.toString(StandardCharsets.UTF_8);
        assertTrue(body.contains("503"));
        assertTrue(body.contains(ErrorCauses.CONFIG_STORE_UNREACHABLE.name()));
        assertTrue(body.contains("Unable to reach the configuration store. Check the logs."));
        assertTrue(body.contains("198.51.100.8"));
        assertFalse(body.contains("ssm.us-east-1.amazonaws.com"));
    }

    @Test
    void writeIfStartupFailed_failsSqsSoTheBatchIsRetried() {
        LambdaContainerStartup.initialize(() -> {
            throw new RuntimeException(new UnknownHostException("ssm.us-east-1.amazonaws.com"));
        });

        String event = """
                {
                  "Records": [
                    { "eventSource": "aws:sqs", "body": "{}" }
                  ]
                }
                """;
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> LambdaContainerStartup.writeIfStartupFailed(
                        new java.io.ByteArrayInputStream(event.getBytes(StandardCharsets.UTF_8)),
                        new java.io.ByteArrayOutputStream()));
        assertTrue(failure.getMessage().contains("ssm"));
        assertTrue(failure.getMessage().contains("Check the logs"));
        assertFalse(failure.getMessage().contains("amazonaws.com"));
    }

    @Test
    void stillFailed_retriesAfterTheCooldown() {
        Clock start = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        LambdaContainerStartup.setClock(start);
        LambdaContainerStartup.initialize(() -> {
            throw new RuntimeException(new UnknownHostException("ssm.us-east-1.amazonaws.com"));
        });

        assertTrue(LambdaContainerStartup.stillFailed(() -> {
            throw new AssertionError("retried before the cooldown");
        }));

        LambdaContainerStartup.setClock(Clock.fixed(start.instant().plusSeconds(61), ZoneOffset.UTC));
        assertFalse(LambdaContainerStartup.stillFailed(() -> {
        }));
        assertFalse(LambdaContainerStartup.failed());
    }
}
