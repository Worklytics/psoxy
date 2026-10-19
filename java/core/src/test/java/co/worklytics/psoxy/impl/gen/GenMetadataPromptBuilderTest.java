package co.worklytics.psoxy.impl.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenMetadataPromptBuilderTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final GenMetadataPromptBuilder promptBuilder = new GenMetadataPromptBuilder(OBJECT_MAPPER);

    @Test
    void classifyUserContent_listsClasses() {
        String user = promptBuilder.classifyUserContent("Classify",
            List.of("Excluded", "Research and Ideation"), "hello");
        assertTrue(user.contains("exactly one of these classes"));
        assertTrue(user.contains("Excluded"));
        List<ChatMessage> messages =
            promptBuilder.toClassifyMessages("Classify",
                List.of("Excluded", "Research and Ideation"), "hello");
        assertEquals(GenMetadataPromptBuilder.SYSTEM_CLASSIFY,
            ((SystemMessage) messages.get(0)).text());
    }

    @Test
    void toMessages_usesComputeSystemPrompt() {
        List<ChatMessage> messages = promptBuilder.toMessages("Extract",
            com.avaulta.gateway.rules.JsonSchema.builder()
                .type("object")
                .properties(java.util.Map.of(
                    "category", com.avaulta.gateway.rules.JsonSchema.builder().type("string").build()))
                .build(),
            "hello");
        assertEquals(GenMetadataPromptBuilder.SYSTEM_COMPUTE,
            ((SystemMessage) messages.get(0)).text());
    }
}
