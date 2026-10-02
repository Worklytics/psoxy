package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchema;
import com.avaulta.gateway.rules.augments.Augment;
import com.avaulta.gateway.rules.augments.GenMetadataInferenceOptions;
import com.avaulta.gateway.rules.augments.GenMetadataInferenceResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
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

        ObjectMapper om = new ObjectMapper();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, new GenMetadataPromptBudget(),
            new GenMetadataChatModelFactory(Set.of(provider)),
            new GenMetadataTokenUsageAccumulator(),
            new GenMetadataPromptBuilder(om),
            new GenMetadataResponseFormats(),
            Clock.systemUTC());

        JsonSchema schema = JsonSchema.builder()
            .type("object")
            .required(List.of("category"))
            .properties(java.util.Map.of(
                "category", JsonSchema.builder()
                    .type("string")
                    .enumValues(List.of("Excluded", "Uncategorized"))
                    .build()))
            .build();

        GenMetadataInferenceResult result = backend.generate("Classify", schema, "hello");
        assertEquals("{\"category\":\"Excluded\"}", result.getText());
        assertNotNull(seen.get());
        assertNull(seen.get().responseFormat(),
            "Bedrock/Nova must not send ResponseFormat (maps to unsupported outputConfig)");
        assertEquals(Augment.GenMetadata.DEFAULT_MAX_OUTPUT_TOKENS, seen.get().maxOutputTokens());
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
        ObjectMapper om2 = new ObjectMapper();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, new GenMetadataPromptBudget(),
            new GenMetadataChatModelFactory(Set.of(provider)),
            new GenMetadataTokenUsageAccumulator(),
            new GenMetadataPromptBuilder(om2),
            new GenMetadataResponseFormats(),
            Clock.systemUTC());
        JsonSchema schema = JsonSchema.builder().type("string").build();

        backend.generate("Classify", schema, "hello",
            GenMetadataInferenceOptions.builder().maxOutputTokens(64).build());
        assertEquals(64, seen.get().maxOutputTokens());

        backend.generate("Classify", schema, "hello",
            GenMetadataInferenceOptions.builder().maxOutputTokens(9999).build());
        assertEquals(9999, seen.get().maxOutputTokens());
    }

    @Test
    void classify_usesLongestClassLengthAsMaxOutputTokens() {
        AtomicReference<ChatRequest> seen = new AtomicReference<>();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                seen.set(request);
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from("Feature"))
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
        ObjectMapper om = new ObjectMapper();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, new GenMetadataPromptBudget(),
            new GenMetadataChatModelFactory(Set.of(provider)),
            new GenMetadataTokenUsageAccumulator(),
            new GenMetadataPromptBuilder(om),
            new GenMetadataResponseFormats(),
            Clock.systemUTC());

        List<String> classes = List.of("Feature", "Uncategorized", "Bug");
        GenMetadataInferenceResult result = backend.classify("Classify", classes, "hello",
            GenMetadataInferenceOptions.builder()
                .maxOutputTokens("Uncategorized".length())
                .build());
        assertEquals("Feature", result.getText());
        assertNull(seen.get().responseFormat());
        assertEquals("Uncategorized".length(), seen.get().maxOutputTokens());
    }
}
