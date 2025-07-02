package co.worklytics.psoxy.gateway.output;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

class ApiDataOutputUtilsTest {

    ApiDataOutputUtils utils;

    @BeforeEach
    public void setup() {
       utils = new ApiDataOutputUtils(() -> UUID.fromString("123e4567-e89b-12d3-a456-426614174000"), Base64.getEncoder());
    }

    @Test
    void buildRawOutputKey() {
        HttpRequest mockRequest = mock(HttpRequest.class);
        when(mockRequest.getUrl()).thenReturn(new com.google.api.client.http.GenericUrl("https://api.example.com/v1/resource"));
        when(mockRequest.getRequestMethod()).thenReturn("GET");
        String key = utils.buildRawOutputKey(mockRequest);

        assertEquals("api.example.com/v1_resource/123e4567-e89b-12d3-a456-426614174000", key);
    }

    @Test
    void testBuildRawOutputKey() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("API_HOST", "api.example.com");
        metadata.put("PATH", "v1/resource");
        ProcessedContent content = ProcessedContent.builder().metadata(metadata).build();
        String key = utils.buildRawOutputKey(content);
        assertEquals("api.example.com/v1_resource/123e4567-e89b-12d3-a456-426614174000", key);
    }

    @Test
    void buildSanitizedOutputKey() {
        HttpEventRequest mockRequest = mock(HttpEventRequest.class);
        when(mockRequest.getHeaders()).thenReturn(Collections.emptyMap());
        when(mockRequest.getHttpMethod()).thenReturn("GET");
        when(mockRequest.getPath()).thenReturn("v1/resource");
        when(mockRequest.getQuery()).thenReturn(Optional.empty());
        when(mockRequest.getBody()).thenReturn(null);
        String key = utils.buildSanitizedOutputKey(mockRequest);
        assertTrue(key.contains("v1_resource"));
    }

    @Test
    void testBuildSanitizedOutputKey() {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("API_HOST", "api.example.com");
        metadata.put("PATH", "v1/resource");
        ProcessedContent content = ProcessedContent.builder().metadata(metadata).build();
        String key = utils.buildSanitizedOutputKey(content);
        assertTrue(key.contains("v1_resource"));
        assertEquals("api.example.com/v1_resource/123e4567-e89b-12d3-a456-426614174000", key);
    }

    @Test
    void responseAsRawProcessedContent() throws Exception {
        HttpRequest mockRequest = mock(HttpRequest.class);
        when(mockRequest.getUrl()).thenReturn(new com.google.api.client.http.GenericUrl("https://api.example.com/v1/resource"));
        when(mockRequest.getRequestMethod()).thenReturn("GET");
        HttpResponse mockResponse = mock(HttpResponse.class);
        when(mockResponse.getContentType()).thenReturn("application/json");
        when(mockResponse.getContentCharset()).thenReturn(StandardCharsets.UTF_8);
        byte[] contentBytes = "{\"foo\":\"bar\"}".getBytes();
        InputStream contentStream = new ByteArrayInputStream(contentBytes);
        when(mockResponse.getContent()).thenReturn(contentStream);
        ProcessedContent processed = utils.responseAsRawProcessedContent(mockRequest, mockResponse);
        assertEquals("application/json", processed.getContentType());
        assertArrayEquals(contentBytes, processed.getContent());
    }

    @Test
    void buildRawMetadata() {
        HttpRequest mockRequest = mock(HttpRequest.class);
        com.google.api.client.http.GenericUrl url = new com.google.api.client.http.GenericUrl("https://api.example.com/v1/resource?foo=bar");
        when(mockRequest.getUrl()).thenReturn(url);
        when(mockRequest.getRequestMethod()).thenReturn("POST");
        Map<String, String> metadata = utils.buildRawMetadata(mockRequest);
        assertEquals("api.example.com", metadata.get("API_HOST"));
        assertEquals("v1/resource", metadata.get("PATH"));
        assertEquals("POST", metadata.get("HTTP_METHOD"));
        assertTrue(metadata.get(ApiDataOutputUtils.OutputObjectMetadata.QUERY_STRING.name()).contains("foo=bar"));
    }

    @Test
    void buildMetadata() {
        HttpEventRequest mockRequest = mock(HttpEventRequest.class);
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", List.of("Bearer token"));
        when(mockRequest.getHeaders()).thenReturn(headers);
        when(mockRequest.getHttpMethod()).thenReturn("GET");
        when(mockRequest.getPath()).thenReturn("v1/resource");
        when(mockRequest.getQuery()).thenReturn(Optional.of("foo=bar"));
        when(mockRequest.getBody()).thenReturn("body".getBytes());
        Map<String, String> metadata = utils.buildMetadata(mockRequest);
        assertEquals("GET", metadata.get("HTTP_METHOD"));
        assertEquals("v1/resource", metadata.get("PATH"));
        assertTrue(metadata.get("QUERY_STRING").contains("foo=bar"));
        assertNotNull(metadata.get("REQUEST_BODY"));
    }

    @ValueSource(
        strings = {
            "Authorization",
            "X-Forwarded-For",
            "User-Agent",
            "X-Forwarded-Proto",
            "Host",
        }
    )
    @ParameterizedTest
    void testIsParameterHeader_not(String header) {
        assertFalse(utils.isParameterHeader(header));
    }

}
