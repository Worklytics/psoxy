package co.worklytics.psoxy.impl.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GenMetadataTokenUsageAccumulatorTest {

    @Test
    void aggregatesAndResets() {
        GenMetadataTokenUsageAccumulator acc = new GenMetadataTokenUsageAccumulator();
        assertFalse(acc.snapshot().hasUsage());

        acc.record(10, 3);
        acc.record(null, 2);
        acc.record(5, null);

        GenMetadataTokenUsageAccumulator.Snapshot snap = acc.snapshot();
        assertTrue(snap.hasUsage());
        assertEquals(3, snap.getCalls());
        assertEquals(15, snap.getInputTokens());
        assertEquals(5, snap.getOutputTokens());

        acc.reset();
        assertFalse(acc.snapshot().hasUsage());
        assertEquals(0, acc.snapshot().getCalls());
    }

    @Test
    void concurrentThreadsDoNotShareCounts() throws Exception {
        GenMetadataTokenUsageAccumulator acc = new GenMetadataTokenUsageAccumulator();
        CountDownLatch started = new CountDownLatch(2);
        CountDownLatch record = new CountDownLatch(1);
        AtomicReference<GenMetadataTokenUsageAccumulator.Snapshot> first = new AtomicReference<>();
        AtomicReference<GenMetadataTokenUsageAccumulator.Snapshot> second = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            pool.submit(() -> {
                started.countDown();
                record.await();
                acc.record(10, 1);
                first.set(acc.snapshot());
                return null;
            });
            pool.submit(() -> {
                started.countDown();
                record.await();
                acc.record(20, 2);
                second.set(acc.snapshot());
                return null;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            record.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, first.get().getCalls());
        assertEquals(10, first.get().getInputTokens());
        assertEquals(1, first.get().getOutputTokens());
        assertEquals(1, second.get().getCalls());
        assertEquals(20, second.get().getInputTokens());
        assertEquals(2, second.get().getOutputTokens());
        assertFalse(acc.snapshot().hasUsage());
    }
}
