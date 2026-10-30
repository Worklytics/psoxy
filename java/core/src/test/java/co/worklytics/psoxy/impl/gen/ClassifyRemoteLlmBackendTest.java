package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassifyRemoteLlmBackendTest {

    @Test
    void classify_usesClassifyModelNotGenMetadataModel() {
        List<String> seenModels = new ArrayList<>();
        List<Integer> seenTimeouts = new ArrayList<>();
        ChatModel model = new ChatModel() {
            @Override
            public ChatResponse chat(ChatRequest request) {
                return ChatResponse.builder().aiMessage(AiMessage.from("Feature")).build();
            }
        };
        GenMetadataChatModelProvider provider = new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig config) {
                return config instanceof BedrockGenMetadataConfig;
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                seenModels.add(config.getModelId());
                seenTimeouts.add(config.getTimeoutSeconds());
                return model;
            }
        };
        GenMetadataConfig host = BedrockGenMetadataConfig.of(BedrockGenMetadataConfig.DEFAULT_MODEL, 30);
        ClassifyConfig classify = ClassifyConfig.from(new co.worklytics.psoxy.gateway.ConfigService() {
            @Override
            public String getConfigPropertyOrError(ConfigProperty property) {
                return getConfigPropertyAsOptional(property).orElseThrow();
            }

            @Override
            public java.util.Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
                if (property == ClassifyConfig.ConfigProperty.CLASSIFY_MODEL) {
                    return java.util.Optional.of("amazon.nova-lite-v1:0");
                }
                if (property == ClassifyConfig.ConfigProperty.CLASSIFY_TIMEOUT_SECONDS) {
                    return java.util.Optional.of("9");
                }
                return java.util.Optional.empty();
            }
        });
        ObjectMapper om = new ObjectMapper();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            host, classify, new GenMetadataPromptBudget(),
            new GenMetadataChatModelFactory(Set.of(provider)),
            new GenMetadataTokenUsageAccumulator(),
            new GenMetadataPromptBuilder(om),
            new GenMetadataResponseFormats(),
            Clock.systemUTC());

        GenMetadataInferenceResult result = backend.classify(
            "Classify", List.of("Feature", "Bugfix"), "hello",
            GenMetadataInferenceOptions.builder().maxOutputTokens(7).build());
        assertEquals("Feature", result.getText());
        assertEquals(List.of("us.amazon.nova-lite-v1:0"), seenModels);
        assertEquals(List.of(9), seenTimeouts);
        assertTrue(seenModels.stream().noneMatch(BedrockGenMetadataConfig.DEFAULT_MODEL::equals));
    }

    @Test
    void classify_embeddedDecideDoesNotCallTheChatFactory() {
        GenMetadataChatModelProvider provider = new GenMetadataChatModelProvider() {
            @Override
            public boolean supports(GenMetadataConfig config) {
                return true;
            }

            @Override
            public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
                throw new AssertionError("chat backend called");
            }
        };
        GenMetadataConfig host = BedrockGenMetadataConfig.of("m", 15);
        ClassifyConfig classify = ClassifyConfig.from(new co.worklytics.psoxy.gateway.ConfigService() {
            @Override
            public String getConfigPropertyOrError(ConfigProperty property) {
                return getConfigPropertyAsOptional(property).orElseThrow();
            }

            @Override
            public java.util.Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
                if (property == ClassifyConfig.ConfigProperty.CLASSIFY_IMPLEMENTATION) {
                    return java.util.Optional.of("embedded-decide");
                }
                if (property == ClassifyConfig.ConfigProperty.CLASSIFY_MODEL_REVISION) {
                    return java.util.Optional.of("261030");
                }
                return java.util.Optional.empty();
            }
        });
        ObjectMapper om = new ObjectMapper();
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            host, classify, new GenMetadataPromptBudget(),
            new GenMetadataChatModelFactory(Set.of(provider)),
            new GenMetadataTokenUsageAccumulator(),
            new GenMetadataPromptBuilder(om),
            new GenMetadataResponseFormats(),
            Clock.systemUTC());
        GenMetadataAugmentException ex = assertThrows(GenMetadataAugmentException.class,
            () -> backend.classify("p", List.of("A", "B"), "x", GenMetadataInferenceOptions.defaults()));
        assertEquals(GenMetadataAugmentException.Code.UNAVAILABLE, ex.getCode());
    }
}
