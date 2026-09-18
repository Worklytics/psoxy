package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchema;
import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LangChain4jGenMetadataBackendAuthFailureTest {

    static class AccessDeniedException extends RuntimeException {
        AccessDeniedException(String message) {
            super(message);
        }
    }

    @Test
    void isAuthFailure_detectsAccessDeniedByClassName() {
        assertTrue(LangChain4jGenMetadataBackend.isAuthFailure(
            new AccessDeniedException("User is not authorized to perform bedrock:InvokeModel")));
        assertTrue(LangChain4jGenMetadataBackend.isAuthFailure(
            new RuntimeException(new AccessDeniedException("nested"))));
        assertTrue(LangChain4jGenMetadataBackend.isAuthFailure(
            new RuntimeException("HTTP 403 Forbidden")));
        assertFalse(LangChain4jGenMetadataBackend.isAuthFailure(
            new RuntimeException("model overloaded somehow else")));
    }

    @Test
    void generate_throwsUnavailableOnAccessDeniedFromCloudModel() {
        GenMetadataConfig config = BedrockGenMetadataConfig.of("us.amazon.nova-2-lite-v1:0", 5);

        ChatModel denied = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new AccessDeniedException("Access denied");
            }
        };
        GenMetadataChatModelProvider bedrock = new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig c) {
                return c instanceof BedrockGenMetadataConfig;
            }

            @Override
            public ChatModel create(GenMetadataConfig c, Path modelCacheDir) {
                return denied;
            }
        };
        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of(bedrock));

        ObjectMapper om = new ObjectMapper();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, om, new GenMetadataPromptBudget(), factory,
            new GenMetadataTokenUsageAccumulator(),
            new GenMetadataPromptBuilder(om),
            new GenMetadataResponseFormats());

        JsonSchema outputSchema = JsonSchema.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of("category", JsonSchema.builder()
                .type("string")
                .enumValues(List.of("Excluded", "Uncategorized"))
                .build()))
            .build();

        GenMetadataAugmentException thrown = assertThrows(GenMetadataAugmentException.class,
            () -> backend.generate("classify", outputSchema, "{\"text\":\"hello\"}"));
        assertEquals(GenMetadataAugmentException.Code.UNAVAILABLE, thrown.getCode());
    }
}
