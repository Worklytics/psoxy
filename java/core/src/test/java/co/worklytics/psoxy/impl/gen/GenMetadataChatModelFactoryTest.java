package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GenMetadataChatModelFactoryTest {

    @Test
    void supports_onlyWhenProviderPresent() {
        GenMetadataChatModelProvider bedrock = stub(GenMetadataConfig.BACKEND_BEDROCK);
        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of(bedrock));
        assertTrue(factory.supports(config(GenMetadataConfig.BACKEND_BEDROCK)));
        assertFalse(factory.supports(config(GenMetadataConfig.BACKEND_VERTEX)));
        assertFalse(factory.supports(config("local")));
    }

    @Test
    void create_dispatchesToMatchingProvider() throws Exception {
        ChatModel bedrockModel = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder().build();
            }
        };
        GenMetadataChatModelProvider bedrock = new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig config) {
                return GenMetadataConfig.BACKEND_BEDROCK.equalsIgnoreCase(config.getBackend());
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                return bedrockModel;
            }
        };
        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of(bedrock));
        assertSame(bedrockModel, factory.create(config(GenMetadataConfig.BACKEND_BEDROCK), null));
    }

    @Test
    void create_throwsWhenNoProviderMatches() {
        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> factory.create(config(GenMetadataConfig.BACKEND_VERTEX), Path.of("/tmp")));
        assertTrue(ex.getMessage().contains("vertex"));
    }

    private static GenMetadataChatModelProvider stub(String backend) {
        return new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig config) {
                return backend.equalsIgnoreCase(config.getBackend());
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static GenMetadataConfig config(String backend) {
        return GenMetadataConfig.builder()
            .backend(backend)
            .modelId("test-model")
            .timeoutSeconds(5)
            .maxInputChars(1024)
            .maxTokens(64)
            .build();
    }
}
