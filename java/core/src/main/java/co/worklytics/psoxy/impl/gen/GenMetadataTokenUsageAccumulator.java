package co.worklytics.psoxy.impl.gen;

import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * Per-thread accumulator for genMetadata LLM token usage.
 *
 * <p>Bulk processing resets at the start of each file on the worker thread; {@link #snapshot()}
 * feeds sanitized-object metadata on that same thread. Concurrent files therefore cannot clobber
 * each other's counts. Inference {@link #record} runs on the caller thread after {@code Future.get},
 * not on the chat-pool worker.
 */
@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class GenMetadataTokenUsageAccumulator {

    private final ThreadLocal<Counters> current = ThreadLocal.withInitial(Counters::new);

    /**
     * Record one completed LLM call. Null counts are treated as zero (provider omitted usage).
     */
    public void record(Integer inputTokenCount, Integer outputTokenCount) {
        current.get().record(inputTokenCount, outputTokenCount);
    }

    public void reset() {
        current.set(new Counters());
    }

    public Snapshot snapshot() {
        return current.get().snapshot();
    }

    private static final class Counters {
        private final AtomicLong inputTokens = new AtomicLong();
        private final AtomicLong outputTokens = new AtomicLong();
        private final AtomicLong calls = new AtomicLong();

        void record(Integer inputTokenCount, Integer outputTokenCount) {
            if (inputTokenCount != null) {
                inputTokens.addAndGet(inputTokenCount);
            }
            if (outputTokenCount != null) {
                outputTokens.addAndGet(outputTokenCount);
            }
            calls.incrementAndGet();
        }

        Snapshot snapshot() {
            return new Snapshot(calls.get(), inputTokens.get(), outputTokens.get());
        }
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
