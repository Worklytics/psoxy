package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.Output;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.stream.IntStream;
import java.util.zip.GZIPInputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// TODO: fix this
@Disabled // this mock approach seems to work in Intellij, but fail via maven
class BatchMergeHandlerTest {

    OutputUtils outputUtils;
    BatchMergeHandler handler;

    @BeforeEach
    void setUp() {
        outputUtils = mock(OutputUtils.class, RETURNS_DEEP_STUBS);
        handler = new BatchMergeHandler(outputUtils);
    }

    private static byte[] gzip(byte[] input) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzipOut = new java.util.zip.GZIPOutputStream(baos)) {
            gzipOut.write(input);
        }
        return baos.toByteArray();
    }

    private static String gunzip(byte[] gzipped) throws IOException {
        try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            return new String(gis.readAllBytes());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1,false",
        "2,false",
        "1,true",
        "2,true",
        "3,true"
    })
    void batchMergeHandlerTest(int elementCount, boolean gzipped) throws Exception {
        String[] contents = IntStream.range(0, elementCount)
            .mapToObj(i -> "{\"foo\": \"bar" + i + "\"}")
                .toArray(String[]::new);
        ProcessedContent[] pcs = new ProcessedContent[elementCount];
        for (int i = 0; i < elementCount; i++) {
            byte[] contentBytes = contents[i].getBytes();
            if (gzipped) {
                contentBytes = gzip(contentBytes);
            }
            pcs[i] = ProcessedContent.builder()
                    .contentType("application/json")
                    .contentEncoding(gzipped ? "gzip" : null)
                    .content(contentBytes)
                    .build();
        }
        handler.handleBatch(Stream.of(pcs));
        ArgumentCaptor<ProcessedContent> captor = ArgumentCaptor.forClass(ProcessedContent.class);
        verify( (Output) outputUtils.forWebhookQueue(), times(1)).write(captor.capture());
        ProcessedContent result = captor.getValue();
        assertEquals("gzip", result.getContentEncoding());
        assertEquals("application/x-ndjson", result.getContentType());
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < elementCount; i++) {
            expected.append(contents[i]);
        }
        assertEquals(expected.toString(), gunzip(result.getContent()));
    }
}
