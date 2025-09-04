package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventResponse;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.zip.GZIPInputStream;

import static co.worklytics.psoxy.ResponseCompressionHandler.GZIP;
import static org.junit.jupiter.api.Assertions.*;

class ResponseCompressionHandlerTest {

    @SneakyThrows
    @ParameterizedTest
    @ValueSource(ints = {0, 512, 1024, 2047, 2048, 100_000})
    void compress(int bodySize) {

        ResponseCompressionHandler responseCompressionHandler = new ResponseCompressionHandler();
        String uncompressed = RandomStringUtils.random(bodySize);

        HttpEventResponse originalResponse =
            HttpEventResponse.builder().statusCode(200).headers(new HashMap<>()).body(uncompressed).build();
        Pair<Boolean, HttpEventResponse> compressedResponse = responseCompressionHandler.compressIfNeeded(originalResponse);


        boolean shouldCompress = responseCompressionHandler.compressionOutweighOverhead(uncompressed);
        boolean isCompressed = compressedResponse.getLeft();
        HttpEventResponse response = compressedResponse.getRight();
        String compressed = response.getBody();
        assertEquals(shouldCompress, isCompressed);

        if (shouldCompress) {
            assertNotEquals(uncompressed, compressed);
            assertTrue(isCompressed);
            assertEquals(uncompressed, responseCompressionHandler.uncompress(compressed));
            assertEquals(GZIP, response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        } else {
            assertEquals(uncompressed, compressed);
            assertFalse(isCompressed);
            assertNull(response.getHeaders().get(HttpHeaders.CONTENT_ENCODING));
        }
    }
}
