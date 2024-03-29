package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

class ResponseCompressionHandler {

    static final String GZIP = "gzip";
    static final int DEFAULT_COMPRESSION_BUFFER_SIZE = 2048;
    static final int MIN_BYTES_TO_COMPRESS = 2048;


    static boolean isCompressionRequested(HttpEventRequest request) {
        return request.getHeader(HttpHeaders.ACCEPT_ENCODING).orElse("none").contains(ResponseCompressionHandler.GZIP);
    }

    @VisibleForTesting
    boolean compressionOutweighOverhead(String body) {
        return StringUtils.length(body) >= MIN_BYTES_TO_COMPRESS;
    }

    /**
     * Compresses content as binary base64
     *
     * @param body
     * @return optional with content if compression has been applied
     */
    Optional<String> compressBodyAndConvertToBase64(String body) {
        if (compressionOutweighOverhead(body)) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream(DEFAULT_COMPRESSION_BUFFER_SIZE)) {
                try (GZIPOutputStream output = new GZIPOutputStream(bos)) {
                    output.write(body.getBytes(StandardCharsets.UTF_8.name()));
                }
                return Optional.ofNullable(Base64.encodeBase64String(bos.toByteArray()));
            } catch (IOException ignored) {
                // do nothing, send uncompressed
            }
        }
        return Optional.empty();
    }

    /**
     * @param response
     * @return (bool, response) - bool indicates if the response has been compressed or not
     */
    Pair<Boolean, HttpEventResponse> compressIfNeeded(HttpEventResponse response) {
        String uncompressed = response.getBody();
        Optional<String> compressedBody = compressBodyAndConvertToBase64(uncompressed);
        HttpEventResponse returnResponse = response;
        boolean compressed = compressedBody.isPresent();
        if (compressed) {
            returnResponse = HttpEventResponse.builder()
                .body(compressedBody.get())
                .statusCode(response.getStatusCode())
                .headers(response.getHeaders())
                .header(HttpHeaders.CONTENT_ENCODING, GZIP)
                .build();
        }
        return Pair.of(compressed, returnResponse);
    }
}
