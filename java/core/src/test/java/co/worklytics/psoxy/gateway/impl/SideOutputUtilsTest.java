package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
    void toGzippedStream() throws Exception {
        String content = "Hello, world!";
        try (InputStream gzipped = utils.toGzippedStream(content, StandardCharsets.UTF_8);
             GZIPInputStream gunzip = new GZIPInputStream(gzipped);
             InputStreamReader reader = new InputStreamReader(gunzip, StandardCharsets.UTF_8);
             BufferedReader buffered = new BufferedReader(reader)) {
            String result = buffered.readLine();
            assertEquals(content, result);
        }
    }
}
