package co.worklytics.psoxy.impl.gen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenMetadataPromptBudgetTest {

    private final GenMetadataPromptBudget promptBudget =
        new GenMetadataPromptBudget(BedrockGenMetadataConfig.of("test-model", 15));

    @Test
    void fitDynamicInput_capsToMaxChars() {
        String input = "x".repeat(5000);
        String fitted = promptBudget.fitDynamicInput(input, 500);
        assertEquals(500, fitted.length());
    }

    @Test
    void fitDynamicInput_doesNotShrinkBudgetBecauseTaskPromptIsLong() {
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

    @Test
    void fitDynamicInput_breaksOnNonWordCharacter() {
        String fitted = promptBudget.fitDynamicInput("hello world extra", 6);
        assertEquals("hello ", fitted);
    }

    @Test
    void effectiveMaxDynamicInputChars_honorsRuleCap() {
        assertEquals(500, promptBudget.effectiveMaxDynamicInputChars(100));
        assertEquals(GenMetadataConfig.DEFAULT_MAX_DYNAMIC_INPUT_CHARS,
            promptBudget.effectiveMaxDynamicInputChars(null));
    }
}
