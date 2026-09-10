package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchemaFilter;
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
    void isAuthOrQuotaFailure_detectsAccessDeniedByClassName() {
        assertTrue(LangChain4jGenMetadataBackend.isAuthOrQuotaFailure(
            new AccessDeniedException("User is not authorized to perform bedrock:InvokeModel")));
        assertTrue(LangChain4jGenMetadataBackend.isAuthOrQuotaFailure(
            new RuntimeException(new AccessDeniedException("nested"))));
        assertTrue(LangChain4jGenMetadataBackend.isAuthOrQuotaFailure(
            new RuntimeException("HTTP 403 Forbidden")));
        assertFalse(LangChain4jGenMetadataBackend.isAuthOrQuotaFailure(
            new RuntimeException("model overloaded somehow else")));
    }

    @Test
    void generate_returnsNullOnAccessDeniedFromCloudModel() {
        GenMetadataConfig config = GenMetadataConfig.builder()
            .backend(GenMetadataConfig.BACKEND_BEDROCK)
            .modelId("anthropic.claude-3-haiku-20240307-v1:0")
            .timeoutSeconds(5)
            .maxInputChars(1024)
            .maxTokens(64)
            .build();

        ChatModel denied = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                throw new AccessDeniedException("Access denied");
            }
        };
        GenMetadataChatModelProvider bedrock = new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig c) {
                return GenMetadataConfig.BACKEND_BEDROCK.equalsIgnoreCase(c.getBackend());
            }

            @Override
            public ChatModel create(GenMetadataConfig c, Path modelCacheDir) {
                return denied;
            }
        };
        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of(bedrock));

        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config,
            new ObjectMapper(),
            new GenMetadataPromptBudget(new ObjectMapper()),
            factory);

        JsonSchemaFilter outputSchema = JsonSchemaFilter.builder()
            .type("object")
            .required(List.of("category"))
            .properties(Map.of("category", JsonSchemaFilter.builder()
                .type("string")
                .enumValues(List.of("Excluded", "Uncategorized"))
                .build()))
            .build();

        assertNull(backend.generate("classify", outputSchema, "{\"text\":\"hello\"}"));
    }
}
