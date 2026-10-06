package co.worklytics.psoxy;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import co.worklytics.psoxy.gateway.ConfigStoreUnreachableException;
import org.junit.jupiter.api.Test;

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

        GcpConfigStoreResponses.write(request, response, new ConfigStoreUnreachableException(
                "Unable to resolve host secretmanager.googleapis.com",
                new java.net.UnknownHostException("secretmanager.googleapis.com")));

        verify(response).setStatusCode(503);
        verify(response).appendHeader("X-Psoxy-Error", "CONFIG_STORE_UNREACHABLE");
        String written = body.toString();
        assertTrue(written.contains("CONFIG_STORE_UNREACHABLE"));
        assertTrue(written.contains("203.0.113.9"));
        assertTrue(written.contains("secretmanager.googleapis.com"));
        assertTrue(written.contains("Error reading configuration from Secret Manager"));
    }
}
