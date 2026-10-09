package co.worklytics.psoxy;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import co.worklytics.psoxy.gateway.ConnectivityFailureResponses;
import co.worklytics.psoxy.gateway.DependencyConnectivityFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GcpConfigStoreResponsesTest {

    @Test
    void writesConfigStoreUnreachableResponse() throws Exception {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeaders()).thenReturn(Map.of(
                "X-Forwarded-For", List.of("203.0.113.9, 198.51.100.1")));
        HttpResponse response = mock(HttpResponse.class);
        OutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(body);

        GcpConfigStoreResponses.write(request, response, responses(),
                DependencyConnectivityFailure.builder()
                        .service("secretmanager")
                        .clientMessage("Unable to reach Secret Manager. Check the logs.")
                        .build(),
                new UnknownHostException("secretmanager.googleapis.com"),
                true);

        verify(response).setStatusCode(503);
        verify(response).appendHeader("X-Psoxy-Error", "CONFIG_STORE_UNREACHABLE");
        String written = body.toString();
        assertTrue(written.contains("CONFIG_STORE_UNREACHABLE"));
        assertTrue(written.contains("203.0.113.9"));
        assertTrue(written.contains("Unable to reach Secret Manager. Check the logs."));
        assertFalse(written.contains("secretmanager.googleapis.com"));
        assertFalse(written.contains("On GCP"));
        assertFalse(written.contains("Error reading configuration from Secret Manager"));
    }

    @Test
    void writesDependentServiceResponseWithoutTheClientError() throws Exception {
        HttpRequest request = mock(HttpRequest.class);
        when(request.getHeaders()).thenReturn(Map.of());
        HttpResponse response = mock(HttpResponse.class);
        OutputStream body = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(body);

        GcpConfigStoreResponses.write(request, response, responses(),
                DependencyConnectivityFailure.builder().build(),
                new UnknownHostException("www.googleapis.com"),
                false);

        verify(response).setStatusCode(503);
        verify(response).appendHeader("X-Psoxy-Error", "DEPENDENT_SERVICE_UNREACHABLE");
        String written = body.toString();
        assertTrue(written.contains("DEPENDENT_SERVICE_UNREACHABLE"));
        assertTrue(written.contains("Unable to reach a dependent service. Check the logs."));
        assertFalse(written.contains("www.googleapis.com"));
        assertFalse(written.contains("Affected service"));
    }

    private static ConnectivityFailureResponses responses() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.registerModule(new Jdk8Module());
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return new ConnectivityFailureResponses(objectMapper);
    }
}
