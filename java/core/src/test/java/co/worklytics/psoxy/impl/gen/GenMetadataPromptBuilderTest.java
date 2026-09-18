package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchema;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenMetadataPromptBuilderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final GenMetadataPromptBuilder promptBuilder = new GenMetadataPromptBuilder(OBJECT_MAPPER);

    @Test
    void classifyUserContent_rootStringAsksForJsonString() {
        JsonSchema schema = JsonSchema.builder()
            .type("string")
            .enumValues(List.of("Excluded", "Research and Ideation"))
            .build();
        String user = promptBuilder.classifyUserContent("Classify", schema, "hello");
        assertTrue(user.contains("JSON string"));
        assertFalse(user.contains("JSON object of the form"));
        List<ChatMessage> messages =
            promptBuilder.toMessages("Classify", schema, "hello");
        assertEquals(GenMetadataPromptBuilder.SYSTEM_CLASSIFY_STRING,
            ((SystemMessage) messages.get(0)).text());
    }

    @Test
    void classifyUserContent_objectSchemaAsksForJsonObject() {
        JsonSchema schema = JsonSchema.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of(
                "category", JsonSchema.builder()
                    .type("string")
                    .enumValues(List.of("Excluded"))
                    .build()))
            .build();
        String user = promptBuilder.classifyUserContent("Classify", schema, "hello");
        assertTrue(user.contains("{\"category\":\"<label>\"}"));
        List<ChatMessage> messages =
            promptBuilder.toMessages("Classify", schema, "hello");
        assertEquals(GenMetadataPromptBuilder.SYSTEM_CLASSIFY,
            ((SystemMessage) messages.get(0)).text());
    }
}
