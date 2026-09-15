package co.worklytics.psoxy.impl.gen;

import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;

/**
 * Thread-safe accumulator for genMetadata LLM token usage across concurrent calls.
 * Bulk processing resets per file; {@link #snapshot()} feeds sanitized-object metadata.
 */
@Singleton
public class GenMetadataTokenUsageAccumulator {

    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong calls = new AtomicLong();

    @Inject
    public GenMetadataTokenUsageAccumulator() {
    }

    /**
     * Record one completed LLM call. Null counts are treated as zero (provider omitted usage).
     */
    public void record(Integer inputTokenCount, Integer outputTokenCount) {
        inputTokens.addAndGet(inputTokenCount != null ? inputTokenCount : 0);
        outputTokens.addAndGet(outputTokenCount != null ? outputTokenCount : 0);
        calls.incrementAndGet();
    }

    public void reset() {
        inputTokens.set(0);
        outputTokens.set(0);
        calls.set(0);
    }

    public Snapshot snapshot() {
        return new Snapshot(calls.get(), inputTokens.get(), outputTokens.get());
    }

    @Value
    public static class Snapshot {
        long calls;
        long inputTokens;
        long outputTokens;

        public boolean hasUsage() {
            return calls > 0 || inputTokens > 0 || outputTokens > 0;
        }
    }
}
