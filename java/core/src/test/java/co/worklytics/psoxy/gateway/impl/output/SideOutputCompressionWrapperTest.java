package co.worklytics.psoxy.gateway.impl.output;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class SideOutputCompressionWrapperTest {

    @Test
    void gzipContent() throws Exception {
        String content = "Hello, world!";
        // Verify that the content is gzipped

        SideOutputCompressionWrapper utils = SideOutputCompressionWrapper.wrap(new NoSideOutput());
        byte[] gzipped = utils.gzipContent(content.getBytes(StandardCharsets.UTF_8));
        try (InputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(gzipped));
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String decompressedContent = reader.readLine();
            assertEquals(content, decompressedContent);
        }
    }
}
