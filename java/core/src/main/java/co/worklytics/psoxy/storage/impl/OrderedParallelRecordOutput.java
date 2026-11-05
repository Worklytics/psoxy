package co.worklytics.psoxy.storage.impl;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import lombok.extern.java.Log;

/**
 * Buffers sanitized records keyed by input order and writes them to a {@link RecordWriter}
 * strictly in sequence. Safe to call {@link #recordComplete(long, Optional)} from multiple threads.
 */
@Log
class OrderedParallelRecordOutput {

    private final RecordWriter writer;
    private final Semaphore backpressurePermits;
    private final Object writeLock = new Object();
    private long nextIndexToWrite = 0;
    private final TreeMap<Long, Optional<Map<String, Object>>> completed = new TreeMap<>();
    private IOException writeFailure;

    OrderedParallelRecordOutput(RecordWriter writer) {
        this(writer, null);
    }

    OrderedParallelRecordOutput(RecordWriter writer, Semaphore backpressurePermits) {
        this.writer = writer;
        this.backpressurePermits = backpressurePermits;
    }

    /**
     * @param outcome empty if the record should be omitted from output; present if it should be written
     */
    void recordComplete(long index, Optional<Map<String, Object>> outcome) throws IOException {
        synchronized (writeLock) {
            throwIfWriteFailed();
            completed.put(index, outcome);
            drainReadyInOrder();
        }
    }

    void assertFullyDrained() throws IOException {
        synchronized (writeLock) {
            throwIfWriteFailed();
            if (!completed.isEmpty()) {
                throw new IllegalStateException(
                    "Parallel record processing did not produce output for index "
                        + nextIndexToWrite);
            }
        }
    }

    private void drainReadyInOrder() throws IOException {
        while (completed.containsKey(nextIndexToWrite)) {
            Optional<Map<String, Object>> outcome = completed.remove(nextIndexToWrite);
            nextIndexToWrite++;
            if (outcome.isPresent()) {
                try {
                    writer.writeRecord(outcome.get());
                } catch (IOException e) {
                    writeFailure = e;
                    throw e;
                }
            }
            if (backpressurePermits != null) {
                backpressurePermits.release();
            }
        }
    }

    private void throwIfWriteFailed() throws IOException {
        if (writeFailure != null) {
            throw writeFailure;
        }
    }
}
