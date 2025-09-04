package co.worklytics.psoxy.gateway;

import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;


/**
 * represents content that has been processed by a proxy instance
 *
 *  (possibly an intermediate step in the pipeline)
 *
 */
@Log
@With
@Builder(toBuilder = true)
@Value
public class ProcessedContent implements Serializable {

    public static final String CONTENT_ENCODING_GZIP = "gzip";

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * the content type of the content, if any
     * eg, application/json, text/html, image/png, ...
     */
    String contentType;

    /**
     * the content encoding of the content, if any
     * eg, gzip, deflate, ...
     */
    String contentEncoding;

    /**
     * charset of the content, if any
     * eg, UTF-8, ISO-8859-1, ...
     */
    @Builder.Default
    Charset contentCharset = StandardCharsets.UTF_8;

    /**
     * metadata about the processing or the content
     */
    @Builder.Default
    Map<String, String> metadata = new HashMap<>();

    /**
     * the actual content
     */
    byte[] content;

    InputStream stream;

    @SneakyThrows
    public byte[] getContent() {
        if (content != null) {
            return content;
        } else if (stream != null) {
            return stream.readAllBytes();
        } else {
            return new byte[0];
        }
    }

    @SneakyThrows
    public InputStream getStream() {
        if (stream != null) {
            return stream;
        } else if (content != null) {
            return new ByteArrayInputStream(content);
        } else {
            return InputStream.nullInputStream();
        }
    }

    /**
     * for convenience, a method to get the content as a string - rather than byte array
     * @return the content as a string, using the specified contentCharset
     */
    @SneakyThrows
    public String getContentAsString() {
        return new String(getContent(), contentCharset);
    }

    public boolean isGzipEncoded() {
        return CONTENT_ENCODING_GZIP.equalsIgnoreCase(contentEncoding);
    }

    /**
     * @return a copy of ProcessedContent, which can be read from multiple times
     * if the original content was a stream, it will be fully read into memory, gzipped, and stored in the content byte array
     * this *hopefully* avoids most mem issues
     *
     */
    public ProcessedContent multiReadableCopy() {
        if (this.stream == null) {
            return this;
        } else {
            try (InputStream originalStream = this.stream;
                   ByteArrayOutputStream baos = new ByteArrayOutputStream();
                   OutputStream output = isGzipEncoded() ? new GZIPOutputStream(baos) : baos) {
                originalStream.transferTo(output);
                byte[] contentBytes = baos.toByteArray();
                return this.toBuilder()
                    .contentEncoding(CONTENT_ENCODING_GZIP)
                    .content(contentBytes)
                    .stream(null)
                    .build();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to create multi-readable copy of ProcessedContent", e);
            }
        }
    }

    /**
     * whether the contentType indicates that the content is a gzip file
     *
     * this suggests that the HTTP server is serving a gzipped file; while that's byte-wise equivalent to a gzip-encoded response from a JSON API,
     * it's semantically different.  eg, the object is a file, that is inherently gzipped at the application-layer
     *
     *
     * `Content-Encoding: gzip` implies gzip applied at the server-layer
     * eg, the client (us) said we accept gzip-encoded responses, and the server choose to provide such
     *
     * @return whether the contentType indicates that the content is a gzip file
     */
    private boolean isGzipFile() {
        return Objects.equals(this.getContentType(), "application/gzip");
    }


    /**
     * if the content is gzipped (either by contentEncoding or contentType), return a decompressed version
     * @return a decompressed version of this ProcessedContent, or this if no decompression was needed
     *
     * @throws IOException
     */
    public ProcessedContent decompressIfNeeded() throws IOException {
        if (this.isGzipEncoded() || isGzipFile()) {
            // NOTE: in isGzipFile(), we assume that uncompressed file is really ndjson or json
            log.info("Decompressing gzip response from source API");
            ProcessedContent.ProcessedContentBuilder builder = this.toBuilder()
                .content(null)
                .stream(new GZIPInputStream(this.getStream()));

            if (isGzipFile()) {
                // we're guessing that real type of this is json or ndjson underneath
                builder.contentType("application/x-ndjson");
            }

            if (this.isGzipEncoded()) {
                builder.contentEncoding(null); // no longer gzip-encoded
            }

            return builder.build();
        } else if (StringUtils.isNotBlank(this.getContentEncoding())) {
            // we only support 'gzip' encoding; as long as our outbound request never specifies that we accept something else, it's all we should get
            // but check just in case
            throw new RuntimeException("Unsupported content encoding returned by source API: " + this.getContentEncoding());
        }
        return this;
    }
}
