package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.Output;

class BatchMergeHandlerWriteFailureTest {

    @Test
    void handleBatchPropagatesOutputWriteFailure() {
        Output.WriteFailure writeFailure = new Output.WriteFailure("failed to write");
        BatchMergeHandler handler = new BatchMergeHandler(new OutputUtils() {
            @Override
            @SuppressWarnings("unchecked")
            public <T extends Output> T forBatchedWebhookContent() {
                return (T) new FailingOutput(writeFailure);
            }
        });

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> handler.handleBatch(Stream.of(
            ProcessedContent.builder()
                .contentType("application/json")
                .content("{}".getBytes(StandardCharsets.UTF_8))
                .build()
        )));

        assertSame(writeFailure, thrown.getCause());
    }

    static class FailingOutput implements Output {
        private final WriteFailure writeFailure;

        FailingOutput(WriteFailure writeFailure) {
            this.writeFailure = writeFailure;
        }

        @Override
        public void write(ProcessedContent content) throws WriteFailure {
            throw writeFailure;
        }

        @Override
        public void write(String key, ProcessedContent content) throws WriteFailure {
            throw writeFailure;
        }
    }
}
