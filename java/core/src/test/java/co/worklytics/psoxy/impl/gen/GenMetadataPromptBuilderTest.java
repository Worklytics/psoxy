package co.worklytics.psoxy.impl.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.ChatRequest;
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
        List<dev.langchain4j.data.message.ChatMessage> messages =
            promptBuilder.toClassifyMessages("Classify",
                List.of("Excluded", "Research and Ideation"), "hello");
        assertEquals(GenMetadataPromptBuilder.SYSTEM_CLASSIFY,
            ((SystemMessage) messages.get(0)).text());
    }

    @Test
    void toMessages_usesComputeSystemPrompt() {
        List<dev.langchain4j.data.message.ChatMessage> messages = promptBuilder.toMessages("Extract",
            com.avaulta.gateway.rules.JsonSchema.builder()
                .type("object")
                .properties(java.util.Map.of(
                    "category", com.avaulta.gateway.rules.JsonSchema.builder().type("string").build()))
                .build(),
            "hello");
        assertEquals(GenMetadataPromptBuilder.SYSTEM_COMPUTE,
            ((SystemMessage) messages.get(0)).text());
    }

    @Test
    void assembleClassify_splitsStaticPrefixFromDynamicInput() {
        GenMetadataPromptAssembly assembly = promptBuilder.assembleClassify(
            "Classify", List.of("A", "B"), "payload");
        assertEquals(3, assembly.getSplitMessages().size());
        assertEquals("payload", assembly.getDynamicUserMessage().singleText());
        assertEquals(
            promptBuilder.classifyUserContent("Classify", List.of("A", "B"), "payload"),
            ((UserMessage) assembly.getUncachedMessages().get(1)).singleText());
    }

    @Test
    void assembleClassify_staticFingerprintStableForSameRule() {
        GenMetadataPromptAssembly a = promptBuilder.assembleClassify(
            "Task", List.of("X"), "one");
        GenMetadataPromptAssembly b = promptBuilder.assembleClassify(
            "Task", List.of("X"), "two");
        assertEquals(
            GenMetadataStaticPromptFingerprint.fromCachedPrefixMessages(a.getCachedPrefixMessages()),
            GenMetadataStaticPromptFingerprint.fromCachedPrefixMessages(b.getCachedPrefixMessages()));
    }
}
