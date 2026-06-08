package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.augments.GenMetadataBackend;
import com.avaulta.gateway.rules.augments.GenMetadataAugmentException;
import com.avaulta.gateway.rules.augments.GenMetadataProcessor;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Local Jlama integration tests for genMetadata token-budget regressions seen in live Copilot PoC runs.
 *
 * <p>Skipped unless {@code -Dpsoxy.genMetadata.integration=true} (or {@code PSOXY_GEN_INTEGRATION_TEST=true})
 * and a Jlama model is already materialized under {@code ~/.jlama} or {@code PSOXY_GEN_MODEL_CACHE}.
 *
 * <p>Run from {@code java/} (not the repo root):
 * {@code mvn test -pl core -am -Pgen-metadata-integration}
 *
 * <p>Equivalent manual invocation:
 * {@code mvn test -pl core -am -Dtest=LangChain4jGenMetadataBackendIntegrationTest -Dpsoxy.genMetadata.integration=true -Dsurefire.failIfNoSpecifiedTests=false}
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LangChain4jGenMetadataBackendIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final GenMetadataPromptBudget PROMPT_BUDGET = new GenMetadataPromptBudget(OBJECT_MAPPER);
    private static final ResourceService NO_REMOTE_RESOURCES = path -> java.util.Optional.empty();
    private static final GenMetadataChatModelFactory CHAT_MODEL_FACTORY =
        new GenMetadataChatModelFactory(NO_REMOTE_RESOURCES);

    private GenMetadataConfig config;
    private Path modelCacheDir;

    @BeforeAll
    void requireLocalModel() throws Exception {
        assumeTrue(GenMetadataIntegrationSupport.enabled(),
            "Set -Dpsoxy.genMetadata.integration=true to run local Jlama tests");
        config = GenMetadataIntegrationSupport.defaultConfig();
        modelCacheDir = GenMetadataIntegrationSupport.modelCacheDir(config);
        try {
            CHAT_MODEL_FACTORY.buildLocal(config, modelCacheDir);
        } catch (Exception e) {
            assumeTrue(false, "Failed to load Jlama model '" + config.getModelId() + "' from "
                + modelCacheDir + ": " + e.getMessage()
                + " (set " + GenMetadataIntegrationSupport.MODEL_CACHE_ENV + " if cached elsewhere)");
        }
    }

    @Test
    void misconfiguredJlamaMaxTokens_rejectsLongCopilotPrompt() throws Exception {
        ChatModel broken = CHAT_MODEL_FACTORY.buildLocal(
            config, modelCacheDir, config.getMaxTokens());

        String inputJson = OBJECT_MAPPER.writeValueAsString(
            GenMetadataIntegrationSupport.longResearchIdeationPrompt());
        List<ChatMessage> messages = GenMetadataPromptBuilder.toMessages(
            GenMetadataIntegrationSupport.copilotTaskPrompt(),
            GenMetadataIntegrationSupport.copilotOutputSchema(),
            inputJson,
            OBJECT_MAPPER);

        Exception failure = null;
        try {
            broken.chat(ChatRequest.builder().messages(messages).build());
        } catch (Exception e) {
            failure = e;
        }
        assertNotNull(failure, "expected misconfigured Jlama maxTokens to reject long Copilot prompt");
        assertTrue(
            GenMetadataIntegrationSupport.messageChainContains(failure, "Prompt exceeds max tokens"),
            "expected Jlama 'Prompt exceeds max tokens' failure, got: " + failure);
    }

    @Test
    void misconfiguredJlamaMaxTokens_yieldsUnparseableJsonForSomePromptLengths() throws Exception {
        GenMetadataBackend brokenBackend = new MisconfiguredJlamaBackend(
            config, modelCacheDir, OBJECT_MAPPER, CHAT_MODEL_FACTORY, config.getMaxTokens());
        GenMetadataProcessor processor = new GenMetadataProcessor(brokenBackend, OBJECT_MAPPER, 4096);

        String taskPrompt = GenMetadataIntegrationSupport.copilotTaskPrompt();
        JsonSchemaFilter schema = GenMetadataIntegrationSupport.copilotOutputSchema();

        boolean sawUnparseable = false;
        for (int inputChars : new int[] {200, 400, 600, 800, 1000, 1200, 1600, 2000}) {
            String input = GenMetadataIntegrationSupport.promptOfLength(inputChars);
            try {
                processor.process(taskPrompt, schema, input);
            } catch (GenMetadataAugmentException e) {
                if (e.getCode() == GenMetadataAugmentException.Code.INFERENCE_FAILED) {
                    sawUnparseable = true;
                    break;
                }
                throw e;
            }
        }
        assertTrue(sawUnparseable,
            "expected misconfigured maxTokens to sometimes return truncated JSON that fails parsing "
                + "(live failure: \"classification\": \"Research and Ide\")");
    }

    @Test
    void productionBackend_inferenceSucceedsForLongCopilotPrompt() {
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, OBJECT_MAPPER, PROMPT_BUDGET, CHAT_MODEL_FACTORY, modelCacheDir);
        String inputJson = serializeQuoted(GenMetadataIntegrationSupport.longResearchIdeationPrompt());
        Object raw = backend.generate(
            GenMetadataIntegrationSupport.copilotTaskPrompt(),
            GenMetadataIntegrationSupport.copilotOutputSchema(),
            inputJson);
        assertNotNull(raw,
            "fixed backend must not fail with 'Prompt exceeds max tokens' on long Copilot prompts");
    }

    @Test
    void productionBackend_classifiesShortResearchPrompt() {
        LangChain4jGenMetadataBackend backend = new LangChain4jGenMetadataBackend(
            config, OBJECT_MAPPER, PROMPT_BUDGET, CHAT_MODEL_FACTORY, modelCacheDir);
        GenMetadataProcessor processor = new GenMetadataProcessor(backend, OBJECT_MAPPER, 4096);

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) processor.process(
            GenMetadataIntegrationSupport.copilotTaskPrompt(),
            GenMetadataIntegrationSupport.copilotOutputSchema(),
            GenMetadataIntegrationSupport.shortResearchIdeationPrompt());

        assertFalse(result.containsKey("type") && "object".equals(result.get("type")),
            "model echoed output schema instead of classification: " + result);

        String category = result.values().stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(GenMetadataIntegrationSupport.COPILOT_CATEGORIES::contains)
            .findFirst()
            .orElse(null);
        assertNotNull(category,
            "expected parseable JSON containing a valid Copilot category, got: " + result);
    }

    private static String serializeQuoted(String text) {
        try {
            return OBJECT_MAPPER.writeValueAsString(text);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Simulates pre-fix backend: passes {@code PSOXY_GEN_MAX_TOKENS} through to Jlama as the KV ceiling
     * and does not apply {@link GenMetadataPromptBudget}.
     */
    static final class MisconfiguredJlamaBackend implements GenMetadataBackend {

        private final GenMetadataConfig config;
        private final Path modelCacheDir;
        private final ObjectMapper objectMapper;
        private final int jlamaMaxTokens;
        private final GenMetadataChatModelFactory chatModelFactory;
        private volatile ChatModel chatModel;

        MisconfiguredJlamaBackend(GenMetadataConfig config, Path modelCacheDir,
                                  ObjectMapper objectMapper, GenMetadataChatModelFactory chatModelFactory,
                                  int jlamaMaxTokens) {
            this.config = config;
            this.modelCacheDir = modelCacheDir;
            this.objectMapper = objectMapper;
            this.chatModelFactory = chatModelFactory;
            this.jlamaMaxTokens = jlamaMaxTokens;
        }

        @Override
        public Object generate(String taskPrompt, JsonSchemaFilter outputSchema, String inputData) {
            try {
                ChatModel model = chatModel();
                List<ChatMessage> messages = GenMetadataPromptBuilder.toMessages(
                    taskPrompt, outputSchema, inputData, objectMapper);
                ChatResponse response = model.chat(ChatRequest.builder().messages(messages).build());
                if (response == null || response.aiMessage() == null) {
                    return null;
                }
                return response.aiMessage().text();
            } catch (Exception e) {
                return null;
            }
        }

        private ChatModel chatModel() throws Exception {
            ChatModel local = chatModel;
            if (local == null) {
                synchronized (this) {
                    local = chatModel;
                    if (local == null) {
                        local = chatModelFactory.buildLocal(config, modelCacheDir, jlamaMaxTokens);
                        chatModel = local;
                    }
                }
            }
            return local;
        }
    }
}
