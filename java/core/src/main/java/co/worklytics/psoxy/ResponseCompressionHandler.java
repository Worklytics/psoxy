package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * not really a 'Handler'; more of a utility to compress responses specifically
 */
public class ResponseCompressionHandler {

    static final String GZIP = "gzip";
    static final int DEFAULT_COMPRESSION_BUFFER_SIZE = 2048;
    static final int MIN_BYTES_TO_COMPRESS = 2048;


    public boolean isCompressionRequested(HttpEventRequest request) {
        return request.getHeader(HttpHeaders.ACCEPT_ENCODING).orElse("none").contains(ResponseCompressionHandler.GZIP);
    }

    @VisibleForTesting
    boolean compressionOutweighOverhead(String body) {
        return StringUtils.length(body) >= MIN_BYTES_TO_COMPRESS;
    }

    /**
     * Compresses content as binary base64
     *
     * @param body to compress
     * @return optional with content if compression has been applied
     */
    Optional<String> compressBodyAndConvertToBase64(String body) {
        if (compressionOutweighOverhead(body)) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream(DEFAULT_COMPRESSION_BUFFER_SIZE)) {
                try (GZIPOutputStream output = new GZIPOutputStream(bos)) {
                    output.write(body.getBytes(StandardCharsets.UTF_8));
                }
                return Optional.ofNullable(Base64.encodeBase64String(bos.toByteArray()));
            } catch (IOException ignored) {
                // do nothing, send uncompressed
            }
        }
        return Optional.empty();
    }

    /**
     * @param response to compress if needed
     * @return (bool, response) - bool indicates if the response has been compressed or not
     */
    Pair<Boolean, HttpEventResponse> compressIfNeeded(HttpEventResponse response) {
        HttpEventResponse responseToReturn = response;
        boolean compressed = false;

        // only bother to check if long enough to compress IF not already gzipped
        if (response.getHeaders().containsKey(HttpHeaders.CONTENT_ENCODING) &&
                Objects.equals(GZIP, response.getHeaders().get(org.apache.hc.core5.http.HttpHeaders.CONTENT_ENCODING))) {
            String uncompressed = response.getBody();
            Optional<String> compressedBody = compressBodyAndConvertToBase64(uncompressed);

            compressed = compressedBody.isPresent();
            if (compressed) {
                responseToReturn = HttpEventResponse.builder()
                    .body(compressedBody.get())
                    .statusCode(response.getStatusCode())
                    .multivaluedHeaders(response.getMultivaluedHeaders().entrySet().stream()
                        .flatMap(entry -> entry.getValue().stream().map(v -> Pair.of(entry.getKey(), v)))
                        .collect(Collectors.toList()))
                    .headers(response.getHeaders())
                    .header(HttpHeaders.CONTENT_ENCODING, GZIP)
                    .build();
            }
        }

        return Pair.of(compressed, responseToReturn);
    }

    public OutputStream wrapOutputStreamIfRequested(HttpEventRequest request, OutputStream outputStream) throws IOException {
        if (isCompressionRequested(request)) {
            return java.util.Base64.getEncoder().wrap(new GZIPOutputStream(outputStream));
        } else {
            return outputStream;
        }
    }


    @VisibleForTesting // really, only for tests
    public String uncompress(String compressBody) throws Exception {
        byte[] compressed = compressBody.getBytes(StandardCharsets.UTF_8);
        try (InputStream base64In =  java.util.Base64.getDecoder().wrap(new ByteArrayInputStream(compressed));
             GZIPInputStream gis = new GZIPInputStream(base64In)) {
            return new String(gis.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
