package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.Output;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BatchMergeHandlerFailureTest {

    @Test
    void handleBatchPropagatesWriteFailures() {
        BatchMergeHandler handler = new BatchMergeHandler(new TestOutputUtils(new Output() {
            @Override
            public void write(ProcessedContent content) throws WriteFailure {
                throw new WriteFailure("failed");
            }

            @Override
            public void write(String key, ProcessedContent content) throws WriteFailure {
                throw new WriteFailure("failed");
            }
        }));

        ProcessedContent content = ProcessedContent.builder()
            .contentType(ContentType.APPLICATION_JSON.getMimeType())
            .content("{\"foo\":\"bar\"}".getBytes(StandardCharsets.UTF_8))
            .build();

        UncheckedIOException thrown = assertThrows(UncheckedIOException.class,
            () -> handler.handleBatch(Stream.of(content)));
        assertInstanceOf(Output.WriteFailure.class, thrown.getCause());
    }

    static class TestOutputUtils extends OutputUtils {
        private final Output output;

        TestOutputUtils(Output output) {
            this.output = output;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends Output> T forBatchedWebhookContent() {
            return (T) output;
        }
    }
}
