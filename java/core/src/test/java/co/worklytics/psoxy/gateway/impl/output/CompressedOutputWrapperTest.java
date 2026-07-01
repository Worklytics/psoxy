package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.output.Output;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class CompressedOutputWrapperTest {

    @Test
    void writeNullContent() throws Exception {
        Output delegate = new NoOutput();
        CompressedOutputWrapper wrapper = CompressedOutputWrapper.wrap(delegate);
        ProcessedContent content = ProcessedContent.builder().content(null).build();
        assertDoesNotThrow(() -> wrapper.write(content));
    }

    @Test
    void writeNullContentAlreadyGzipEncoded() throws Exception {
        Output delegate = new NoOutput();
        CompressedOutputWrapper wrapper = CompressedOutputWrapper.wrap(delegate);
        ProcessedContent content = ProcessedContent.builder()
            .content(null)
            .contentEncoding(CompressedOutputWrapper.COMPRESSION_TYPE)
            .build();
        assertDoesNotThrow(() -> wrapper.write(content));
    }

    @Test
    void gzipContent() throws Exception {
        String content = "Hello, world!";
        // Verify that the content is gzipped

        CompressedOutputWrapper utils = CompressedOutputWrapper.wrap(new NoOutput());
        byte[] gzipped = utils.gzipContent(content.getBytes(StandardCharsets.UTF_8));
        try (InputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(gzipped));
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String decompressedContent = reader.readLine();
            assertEquals(content, decompressedContent);
        }
    }
}
