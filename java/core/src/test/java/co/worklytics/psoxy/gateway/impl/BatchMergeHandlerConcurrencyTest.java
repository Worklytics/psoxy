package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.Output;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.RepeatedTest;
import org.mockito.ArgumentCaptor;

/**
 * Concurrency tests for {@link BatchMergeHandler}.
 *
 * Verifies that two concurrent handleBatch() calls with different content produce
 * separate output files with no row interleaving between batches.
 */
// TODO: fix this — the mock approach works in IntelliJ but fails via Maven (same issue as BatchMergeHandlerTest)
@Disabled
class BatchMergeHandlerConcurrencyTest {

    OutputUtils outputUtils1;
    OutputUtils outputUtils2;
    BatchMergeHandler handler1;
    BatchMergeHandler handler2;

    @BeforeEach
    void setUp() {
        // each handler gets its own OutputUtils mock to verify independent output
        outputUtils1 = mock(OutputUtils.class, RETURNS_DEEP_STUBS);
        outputUtils2 = mock(OutputUtils.class, RETURNS_DEEP_STUBS);
        handler1 = new BatchMergeHandler(outputUtils1);
        handler2 = new BatchMergeHandler(outputUtils2);
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

    /**
     * Two threads simultaneously call handleBatch() with different content.
     * Verify that each output contains only its own rows, with no interleaving.
     */
    @SneakyThrows
    @RepeatedTest(5)
    void concurrentBatches_noInterleaving() {
        int batchSize = 5;

        // batch A: rows with "alpha" prefix
        ProcessedContent[] batchA = IntStream.range(0, batchSize)
                .mapToObj(i -> ProcessedContent.builder()
                        .contentType("application/json")
                        .content(("{\"source\": \"alpha\", \"index\": " + i + "}").getBytes())
                        .build())
                .toArray(ProcessedContent[]::new);

        // batch B: rows with "beta" prefix
        ProcessedContent[] batchB = IntStream.range(0, batchSize)
                .mapToObj(i -> ProcessedContent.builder()
                        .contentType("application/json")
                        .content(("{\"source\": \"beta\", \"index\": " + i + "}").getBytes())
                        .build())
                .toArray(ProcessedContent[]::new);

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                handler1.handleBatch(Stream.of(batchA));
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                handler2.handleBatch(Stream.of(batchB));
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        t1.start();
        t2.start();
        t1.join(10000);
        t2.join(10000);

        if (error.get() != null) {
            throw new AssertionError("Thread threw exception during handleBatch", error.get());
        }

        // verify handler1 wrote only alpha rows
        ArgumentCaptor<ProcessedContent> captor1 = ArgumentCaptor.forClass(ProcessedContent.class);
        verify((Output) outputUtils1.forBatchedWebhookContent(), times(1)).write(captor1.capture());
        String output1 = gunzip(captor1.getValue().getContent());
        assertTrue(output1.contains("alpha"), "Handler1 output should contain alpha rows");
        assertTrue(!output1.contains("beta"), "Handler1 output must NOT contain beta rows");

        // verify handler2 wrote only beta rows
        ArgumentCaptor<ProcessedContent> captor2 = ArgumentCaptor.forClass(ProcessedContent.class);
        verify((Output) outputUtils2.forBatchedWebhookContent(), times(1)).write(captor2.capture());
        String output2 = gunzip(captor2.getValue().getContent());
        assertTrue(output2.contains("beta"), "Handler2 output should contain beta rows");
        assertTrue(!output2.contains("alpha"), "Handler2 output must NOT contain alpha rows");
    }
}
