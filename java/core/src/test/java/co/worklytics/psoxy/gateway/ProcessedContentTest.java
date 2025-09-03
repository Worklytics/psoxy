package co.worklytics.psoxy.gateway;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

class ProcessedContentTest {

    @Test
    void multiReadableCopy_nonGzipped() throws IOException {
        String data = "Hello, world!";
        ProcessedContent original = ProcessedContent.builder()
            .contentType("text/plain")
            .contentEncoding(null)
            .stream(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8)))
            .build();

        ProcessedContent copy = original.multiReadableCopy();

        assertNotNull(copy.getContent());
        assertEquals(data, new String(copy.getContent(), StandardCharsets.UTF_8));
        // Can read multiple times
        assertEquals(data, new String(copy.getContent(), StandardCharsets.UTF_8));
        assertNull(copy.getStream()); // Should be null per implementation
    }

    @Test
    void multiReadableCopy_gzipped() throws IOException {
        String data = "Hello, gzipped world!";
        ProcessedContent original = ProcessedContent.builder()
            .contentType("text/plain")
            .contentEncoding(ProcessedContent.CONTENT_ENCODING_GZIP)
            .stream(new GZIPInputStream(new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8))))
            .build();

        ProcessedContent copy = original.multiReadableCopy();

        assertNotNull(copy.getContent());
        // Content should be gzipped
        assertEquals(ProcessedContent.CONTENT_ENCODING_GZIP, copy.getContentEncoding());
        // Can read multiple times
        assertNotNull(copy.getContent());
        assertNull(copy.getStream());

        // Decompress to verify content
        GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(copy.getContent()));
        byte[] decompressed = gis.readAllBytes();
        assertEquals(data, new String(decompressed, StandardCharsets.UTF_8));
    }
}

