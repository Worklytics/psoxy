package co.worklytics.psoxy.impl.gen;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;

class LangChain4jGenMetadataBackendTimeoutTest {

    @Test
    void chatWithTimeout_returnsNullWhenModelExceedsLimit() throws Exception {
        GenMetadataConfig config = BedrockGenMetadataConfig.of("test-model", 1);

        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of());
        ObjectMapper om = new ObjectMapper();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, om, new GenMetadataPromptBudget(), factory,
            new GenMetadataTokenUsageAccumulator(),
            new GenMetadataPromptBuilder(om),
            new GenMetadataResponseFormats());

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

        ChatResponse response = backend.chatWithTimeout(
            slowModel, List.of(UserMessage.from("hello")), null, 64);
        assertNull(response);
    }
}
