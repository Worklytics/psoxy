package co.worklytics.psoxy.impl.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenMetadataPromptTokenEstimateTest {

    @Test
    void estimatedTokens_ceilsByCharBudget() {
        assertEquals(0, GenMetadataPromptTokenEstimate.estimatedTokens(""));
        assertEquals(1, GenMetadataPromptTokenEstimate.estimatedTokens("abcd"));
        assertEquals(2, GenMetadataPromptTokenEstimate.estimatedTokens("abcde"));
    }
}
