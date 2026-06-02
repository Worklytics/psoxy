package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenMetadataPromptBudgetTest {

    private GenMetadataPromptBudget promptBudget;

    @BeforeEach
    void setUp() {
        promptBudget = new GenMetadataPromptBudget(new ObjectMapper());
    }

    @Test
    void fitInputData_respectsMaxInputChars() {
        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("object")
            .properties(Map.of("category", JsonSchemaFilter.builder().type("string").build()))
            .build();
        String input = "x".repeat(5000);
        String fitted = promptBudget.fitInputData(input, "Classify", schema, 1024, 8192, 256);
        assertTrue(fitted.length() <= 1024);
    }

    @Test
    void fitInputData_reservesOutputTokensWithinContext() {
        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("object")
            .properties(Map.of("category", JsonSchemaFilter.builder().type("string").build()))
            .build();
        String taskPrompt = "Classify";
        String input = "x".repeat(10_000);
        int contextLength = 512;
        int maxOutputTokens = 256;
        String fitted = promptBudget.fitInputData(
            input, taskPrompt, schema, 10_000, contextLength, maxOutputTokens);
        assertTrue(fitted.length() < input.length());
        int contextChars = (contextLength - maxOutputTokens - 64) * 4;
        assertTrue(fitted.length() <= contextChars);
    }

    @Test
    void fitInputData_leavesShortInputUnchanged() {
        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("object")
            .properties(Map.of("category", JsonSchemaFilter.builder().type("string").build()))
            .build();
        String input = "short prompt";
        String fitted = promptBudget.fitInputData(input, "Classify", schema, 4096, 8192, 256);
        assertEquals(input, fitted);
    }
}
