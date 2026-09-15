package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LangChain4jGenMetadataBackendBedrockResponseFormatTest {

    @Test
    void bedrock_omitsResponseFormatOnChatRequest() {
        AtomicReference<ChatRequest> seen = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                seen.set(request);
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from("{\"category\":\"Excluded\"}"))
                    .build();
            }
        };

        GenMetadataChatModelProvider provider = new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig config) {
                return GenMetadataConfig.BACKEND_BEDROCK.equalsIgnoreCase(config.getBackend());
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                return model;
            }
        };

        GenMetadataConfig config = GenMetadataConfig.builder()
            .backend(GenMetadataConfig.BACKEND_BEDROCK)
            .modelId(GenMetadataConfig.DEFAULT_BEDROCK_MODEL)
            .timeoutSeconds(30)
            .maxInputChars(4096)
            .maxTokens(256)
            .build();

        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config,
            new ObjectMapper(),
            new GenMetadataPromptBudget(new ObjectMapper()),
            new GenMetadataChatModelFactory(Set.of(provider)));

        JsonSchemaFilter schema = JsonSchemaFilter.builder()
            .type("object")
            .required(List.of("category"))
            .properties(java.util.Map.of(
                "category", JsonSchemaFilter.builder()
                    .type("string")
                    .enumValues(List.of("Excluded", "Uncategorized"))
                    .build()))
            .build();

        Object result = backend.generate("Classify", schema, "hello");
        assertEquals("{\"category\":\"Excluded\"}", result);
        assertNotNull(seen.get());
        assertNull(seen.get().responseFormat(),
            "Bedrock/Nova must not send ResponseFormat (maps to unsupported outputConfig)");
    }
}
