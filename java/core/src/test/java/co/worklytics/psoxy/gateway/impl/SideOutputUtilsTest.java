package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SideOutputUtilsTest {

    SideOutputUtils utils = new SideOutputUtils();

    @Test
    void canonicalResponseKey() {
        // Mock HttpEventRequest
        HttpEventRequest request = mock(HttpEventRequest.class);
        when(request.getHttpMethod()).thenReturn("GET");
        when(request.getHeader("Host")).thenReturn(Optional.of("example.com"));
        when(request.getHeaders()).thenReturn(Collections.emptyMap());
        when(request.getPath()).thenReturn("/api/v1/resource/");
        when(request.getQuery()).thenReturn(Optional.of("b=2&a=1"));

        String key = utils.canonicalResponseKey(request);

        // The expected key is: GET_example.com/api/v1/resource_a hash of the sorted query string
        // Since hashQueryString sorts and hashes, let's compute it here for assertion
        String expectedQueryHash = org.apache.commons.codec.digest.DigestUtils.md5Hex("a=1&b=2");

        String expected = "GET_example.com/api/v1/resource_" + expectedQueryHash;

        assertEquals(expected, key);
    }

    @Test
    void gzipContent() throws Exception {
        String content = "Hello, world!";
            // Verify that the content is gzipped
           byte[] gzipped = utils.gzipContent(content, StandardCharsets.UTF_8);
            try (InputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(gzipped));
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String decompressedContent = reader.readLine();
                assertEquals(content, decompressedContent);
            }
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
