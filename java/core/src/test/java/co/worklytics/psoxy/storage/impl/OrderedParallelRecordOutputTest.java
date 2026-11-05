package co.worklytics.psoxy.storage.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;

class OrderedParallelRecordOutputTest {

    @Test
    void writesRecordsInInputOrderDespiteOutOfOrderCompletion() throws Exception {
        Configuration jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

        StringWriter stringWriter = new StringWriter();
        NdjsonRecordWriter writer = new NdjsonRecordWriter(stringWriter, jsonConfiguration);
        writer.beginRecordSet();

        OrderedParallelRecordOutput orderedOutput = new OrderedParallelRecordOutput(writer);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            orderedOutput.recordComplete(2, Optional.of(record("second")));
            submitDelayedRecord(executor, orderedOutput, startGate, 0, "first", 10);
            submitDelayedRecord(executor, orderedOutput, startGate, 1, "middle", 40);

            startGate.countDown();
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test workers");
            }
            orderedOutput.assertFullyDrained();
        } finally {
            executor.shutdownNow();
            writer.endRecordSet();
            writer.close();
        }

        assertEquals(
            "{\"id\":\"first\"}\n{\"id\":\"middle\"}\n{\"id\":\"second\"}\n",
            stringWriter.toString());
    }

    @Test
    void skipsRecordsMarkedEmpty() throws Exception {
        Configuration jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

        StringWriter stringWriter = new StringWriter();
        NdjsonRecordWriter writer = new NdjsonRecordWriter(stringWriter, jsonConfiguration);
        writer.beginRecordSet();

        OrderedParallelRecordOutput orderedOutput = new OrderedParallelRecordOutput(writer);
        orderedOutput.recordComplete(0, Optional.of(record("keep")));
        orderedOutput.recordComplete(1, Optional.empty());
        orderedOutput.recordComplete(2, Optional.of(record("also-keep")));
        orderedOutput.assertFullyDrained();
        writer.endRecordSet();
        writer.close();

        assertEquals("{\"id\":\"keep\"}\n{\"id\":\"also-keep\"}\n", stringWriter.toString());
    }

    @Test
    void failsWhenRecordMissingFromSequence() throws IOException {
        Configuration jsonConfiguration = Configuration.builder()
            .jsonProvider(new JacksonJsonProvider())
            .mappingProvider(new JacksonMappingProvider())
            .build();

        StringWriter stringWriter = new StringWriter();
        NdjsonRecordWriter writer = new NdjsonRecordWriter(stringWriter, jsonConfiguration);
        writer.beginRecordSet();

        OrderedParallelRecordOutput orderedOutput = new OrderedParallelRecordOutput(writer);
        orderedOutput.recordComplete(0, Optional.of(record("only")));
        orderedOutput.recordComplete(2, Optional.of(record("gap")));
        assertThrows(IllegalStateException.class, orderedOutput::assertFullyDrained);
    }

    private static void submitDelayedRecord(ExecutorService executor,
                                            OrderedParallelRecordOutput orderedOutput,
                                            CountDownLatch startGate,
                                            long index,
                                            String id,
                                            long delayMs) {
        executor.submit(() -> {
            try {
                startGate.await(30, TimeUnit.SECONDS);
                Thread.sleep(delayMs);
                orderedOutput.recordComplete(index, Optional.of(record(id)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static Map<String, Object> record(String id) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", id);
        return record;
    }
}
