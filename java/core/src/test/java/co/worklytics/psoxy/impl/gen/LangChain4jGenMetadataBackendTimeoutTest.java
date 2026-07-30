package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNull;

class LangChain4jGenMetadataBackendTimeoutTest {

    @Test
    void chatWithTimeout_returnsNullWhenModelExceedsLimit() throws Exception {
        GenMetadataConfig config = GenMetadataConfig.builder()
            .backend(GenMetadataConfig.BACKEND_LOCAL)
            .modelId("test-model")
            .timeoutSeconds(1)
            .maxInputChars(4096)
            .maxTokens(64)
            .build();

        ResourceService noModels = path -> Optional.empty();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config,
            new ObjectMapper(),
            new GenMetadataPromptBudget(new ObjectMapper()),
            new GenMetadataChatModelFactory(noModels),
            Files.createTempDirectory("psoxy-jlama-timeout-test"));

        ChatModel slowModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                try {
                    Thread.sleep(2_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ChatResponse.builder().build();
            }
        };

        List<ChatMessage> messages = List.of(UserMessage.from("hello"));
        ChatResponse response = backend.chatWithTimeout(slowModel, messages);
        assertNull(response);
    }
}
