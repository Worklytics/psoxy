package co.worklytics.psoxy.impl.gen;

import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.NoArgsConstructor;
import lombok.Value;

/**
 * Per-request accumulator for genMetadata LLM token usage.
 *
 * <p>{@link #reset()} on the bulk/API worker thread starts a file scope; {@link #snapshot()} on that
 * same thread reads totals for object metadata. {@link #record} uses that thread's scope when
 * present. Parallel bulk record workers must run inference inside
 * {@link #withFileScope(FileScopeHandle, Runnable)} so LLM calls on pool threads contribute to the
 * same scope. Separate concurrent invocations on different threads keep isolated scopes via
 * {@link ThreadLocal}.
 */
@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class GenMetadataTokenUsageAccumulator {

    private final ThreadLocal<Counters> current = ThreadLocal.withInitial(Counters::new);

    /**
     * Opaque handle to the active file scope on the thread that called {@link #reset()}.
     */
    @Value
    public static class FileScopeHandle {
        Counters counters;

        private FileScopeHandle(Counters counters) {
            this.counters = counters;
        }
    }

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

    /**
     * Capture the request thread's file scope for propagation to parallel bulk workers.
     */
    public FileScopeHandle currentFileScopeHandle() {
        return new FileScopeHandle(current.get());
    }

    /**
     * Run {@code action} with the given file scope bound on the current thread (for worker threads).
     */
    public void withFileScope(FileScopeHandle handle, Runnable action) {
        Counters previous = current.get();
        current.set(handle.counters);
        try {
            action.run();
        } finally {
            if (previous != null) {
                current.set(previous);
            } else {
                current.remove();
            }
        }
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
