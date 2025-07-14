package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.output.Output;
import lombok.AllArgsConstructor;
import lombok.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/**
 * decorator for Output that compresses the content before writing it to the underlying SideOutput.
 */
@AllArgsConstructor(staticName = "wrap")
public class CompressedOutputWrapper implements Output {

    static final String COMPRESSION_TYPE = "gzip";

    @NonNull
    Output delegate;

    @Override
    public void write(ProcessedContent content) throws WriteFailure {
        write(null, content);
    }

    @Override
    public void write(String key, ProcessedContent content) throws WriteFailure {
        try {
            if (!Objects.equals(COMPRESSION_TYPE, content.getContentEncoding())) {
                byte[] compressedContent = gzipContent(content.getContent());
                content = content.withContentEncoding(COMPRESSION_TYPE).withContent(compressedContent);
            }
            delegate.write(key, content);
        } catch (Exception e) {
            throw new WriteFailure("Failed to write compressed content", e);
        }
    }

    /**
     * gzip the content
     *
     * @param content to compress
     * @return a byte[] reflecting gzip-encoding of the content
     */
    byte[] gzipContent(@NonNull byte[] content) throws WriteFailure {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(content);
            gzipOutputStream.finish();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new WriteFailure("Failed to gzip content", e);
        }
    }
}
