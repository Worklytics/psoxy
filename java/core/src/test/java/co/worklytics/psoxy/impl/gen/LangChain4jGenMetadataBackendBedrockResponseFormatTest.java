package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.augments.Augment;
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
                return config instanceof BedrockGenMetadataConfig;
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                return model;
            }
        };

        GenMetadataConfig config = BedrockGenMetadataConfig.of(BedrockGenMetadataConfig.DEFAULT_MODEL, 30);

        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config,
            new ObjectMapper(),
            new GenMetadataPromptBudget(),
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
        assertEquals(Augment.GenMetadata.DEFAULT_MAX_TOKENS, seen.get().maxOutputTokens());
    }

    @Test
    void generate_usesRuleMaxTokens() {
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
                return true;
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                return model;
            }
        };
        GenMetadataConfig config = BedrockGenMetadataConfig.of(BedrockGenMetadataConfig.DEFAULT_MODEL, 30);
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config,
            new ObjectMapper(),
            new GenMetadataPromptBudget(),
            new GenMetadataChatModelFactory(Set.of(provider)));
        JsonSchemaFilter schema = JsonSchemaFilter.builder().type("string").build();

        backend.generate("Classify", schema, "hello", 64);
        assertEquals(64, seen.get().maxOutputTokens());

        backend.generate("Classify", schema, "hello", 9999);
        assertEquals(9999, seen.get().maxOutputTokens());
    }
}
