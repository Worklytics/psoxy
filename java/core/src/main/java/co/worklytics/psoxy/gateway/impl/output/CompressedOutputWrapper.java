package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.output.Output;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

/**
 * decorator for Output that compresses the content before writing it to the underlying SideOutput.
 *
 *
 */
@AllArgsConstructor(staticName = "wrap")
public class CompressedOutputWrapper implements Output {

    static final String COMPRESSION_TYPE = "gzip";

    @NonNull
    Output delegate;

    @SneakyThrows
    @Override
    public void write(ProcessedContent content) {
        write(null, content);
    }

    @SneakyThrows
    @Override
    public void write(String key, ProcessedContent content) {

        if (!Objects.equals(COMPRESSION_TYPE, content.getContentEncoding())) {
            byte[] compressedContent = gzipContent(content.getContent());
            content = content.withContentEncoding("gzip").withContent(compressedContent);
        }

        delegate.write(key, content);
    }

    /**
     * gzip the content
     *
     * @param content to compress
     * @return a byte[] reflecting gzip-encoding of the content
     */
    @SneakyThrows
    byte[] gzipContent(@NonNull byte[] content) {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
            gzipOutputStream.write(content);
            gzipOutputStream.finish();
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to gzip content", e);
        }
    }
}
