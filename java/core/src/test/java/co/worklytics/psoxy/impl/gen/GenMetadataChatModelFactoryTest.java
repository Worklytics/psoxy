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
        GenMetadataChatModelProvider bedrock = stubBedrock();
        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of(bedrock));
        assertTrue(factory.supports(BedrockGenMetadataConfig.of("test-model", 5)));
        assertFalse(factory.supports(VertexGenMetadataConfig.of("test-model", "global", 5)));
        assertFalse(factory.supports(new GenMetadataConfig.Unsupported("local")));
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
                return config instanceof BedrockGenMetadataConfig;
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                return bedrockModel;
            }
        };
        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of(bedrock));
        assertSame(bedrockModel, factory.create(BedrockGenMetadataConfig.of("test-model", 5), null));
    }

    @Test
    void create_throwsWhenNoProviderMatches() {
        GenMetadataChatModelFactory factory = new GenMetadataChatModelFactory(Set.of());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> factory.create(VertexGenMetadataConfig.of("test-model", "global", 5), Path.of("/tmp")));
        assertTrue(ex.getMessage().contains("vertex"));
    }

    private static GenMetadataChatModelProvider stubBedrock() {
        return new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig config) {
                return config instanceof BedrockGenMetadataConfig;
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
