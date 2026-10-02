package co.worklytics.psoxy.impl.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenMetadataPromptBudgetTest {

    private final GenMetadataPromptBudget promptBudget = new GenMetadataPromptBudget();

    @Test
    void fitDynamicInput_capsToEstimatedTokensNotCharsOfWholePrompt() {
        String input = "x".repeat(5000);
        String fitted = promptBudget.fitDynamicInput(input, 100);
        assertEquals(100 * GenMetadataPromptBudget.CHARS_PER_TOKEN_ESTIMATE, fitted.length());
    }

    @Test
    void fitDynamicInput_doesNotShrinkBudgetBecauseTaskPromptIsLong() {
        // Static prompt length is irrelevant: only the dynamic corpus is capped.
        String input = "hello world";
        assertEquals(input, promptBudget.fitDynamicInput(input, 500));
    }

    @Test
    void fitDynamicInput_leavesShortInputUnchanged() {
        String input = "short prompt";
        assertEquals(input, promptBudget.fitDynamicInput(input, 256));
    }

    @Test
    void fitDynamicInput_zeroBudgetYieldsEmpty() {
        assertTrue(promptBudget.fitDynamicInput("abc", 0).isEmpty());
    }
}
