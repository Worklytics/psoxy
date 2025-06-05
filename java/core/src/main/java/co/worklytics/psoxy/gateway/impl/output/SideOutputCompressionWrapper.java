package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.output.SideOutput;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

/**
 * decorator for SideOutput that compresses the content before writing it to the underlying SideOutput.
 *
 *
 */
@AllArgsConstructor(staticName = "wrap")
public class SideOutputCompressionWrapper implements SideOutput {

    @NonNull
    SideOutput delegate;

    @Override
    public void write(HttpEventRequest request, ProcessedContent content) throws IOException {

        byte[] compressedContent = gzipContent(content.getContent());

        delegate.write(request, content.withContentEncoding("gzip").withContent(compressedContent));
    }

    /**
     * gzip the content
     *
     * @param content  to compress
     * @return a byte[] reflecting gzip-encoding of the content
     */
    @SneakyThrows
    public byte[] gzipContent(@NonNull byte[] content) {
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
