package co.worklytics.psoxy.impl.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
