package co.worklytics.psoxy.impl.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;

class LangChain4jGenMetadataBackendTokenUsageTest {

    @Test
    void recordsTokenUsageFromChatResponse() {
        GenMetadataTokenUsageAccumulator accumulator = new GenMetadataTokenUsageAccumulator();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"category\":\"Excluded\"}"))
                    .tokenUsage(new TokenUsage(42, 7, 49))
                    .build();
            }
        };

        GenMetadataChatModelProvider provider = new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig config) {
                return GenMetadataConfig.BACKEND_VERTEX.equalsIgnoreCase(config.getBackend());
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                return model;
            }
        };

        GenMetadataConfig config = GenMetadataConfig.builder()
            .backend(GenMetadataConfig.BACKEND_VERTEX)
            .modelId("gemini-test")
            .timeoutSeconds(30)
            .maxInputChars(4096)
            .maxTokens(256)
            .build();

        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config,
            new ObjectMapper(),
            new GenMetadataPromptBudget(new ObjectMapper()),
            new GenMetadataChatModelFactory(Set.of(provider)),
            accumulator);

        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("object")
            .properties(java.util.Map.of(
                "category", JsonSchemaFilter.builder()
                    .type("string")
                    .enumValues(List.of("Excluded"))
                    .build()))
            .build();

        Object result = backend.generate("Classify", schema, "hello", null);
        assertEquals("{\"category\":\"Excluded\"}", result);

        GenMetadataTokenUsageAccumulator.Snapshot snap = accumulator.snapshot();
        assertTrue(snap.hasUsage());
        assertEquals(1, snap.getCalls());
        assertEquals(42, snap.getInputTokens());
        assertEquals(7, snap.getOutputTokens());
    }
}
