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
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SideOutputUtilsTest {

    SideOutputUtils utils = new SideOutputUtils();

    @Test
    void canonicalResponseKey() {
        // Stub HttpEventRequest
        HttpEventRequest request = new HttpEventRequest() {

            @Override
            public byte[] getBody() {
                return new byte[0]; // Not relevant for this test
            }

            @Override
            public Optional<String> getClientIp() {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> isHttps() {
                return Optional.ofNullable(false); // Not relevant for this test
            }

            @Override
            public String getHttpMethod() {
                return "GET";
            }

            @Override
            public Optional<String> getHeader(String name) {
                if ("Host".equals(name)) return Optional.of("example.com");
                return Optional.empty();
            }

            @Override
            public Map<String, List<String>> getHeaders() {
                return Collections.emptyMap();
            }

            @Override
            public String getPath() {
                return "/api/v1/resource/";
            }

            @Override
            public Optional<String> getQuery() {
                return Optional.of("b=2&a=1");
            }

            @Override
            public Optional<List<String>> getMultiValueHeader(String headerName) {
                return Optional.empty(); // Not relevant for this test
            }
        };

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
