package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassifyConfigTest {

    @Test
    void from_defaultsToRemoteLlm() {
        ClassifyConfig config = ClassifyConfig.from(configMap(Map.of()));
        assertEquals(ClassifyConfig.Implementation.REMOTE_LLM, config.getImplementation());
        assertNull(config.getModelId());
        assertNull(config.getModelRevision());
        assertEquals(ClassifyConfig.DEFAULT_TIMEOUT_SECONDS, config.getTimeoutSeconds());
        assertEquals(ClassifyConfig.DEFAULT_MAX_ATTEMPTS, config.getMaxAttempts());
        assertEquals("us.amazon.nova-2-lite-v1:0", config.remoteModelId("us.amazon.nova-2-lite-v1:0"));
    }

    @Test
    void from_remoteModelDoesNotRequireRevision() {
        ClassifyConfig config = ClassifyConfig.from(configMap(Map.of(
            ClassifyConfig.ConfigProperty.CLASSIFY_MODEL.name(), "anthropic.claude-3-haiku-20240307-v1:0",
            ClassifyConfig.ConfigProperty.CLASSIFY_MODEL_REVISION.name(), "main",
            ClassifyConfig.ConfigProperty.CLASSIFY_TIMEOUT_SECONDS.name(), "9",
            ClassifyConfig.ConfigProperty.CLASSIFY_RETRIES.name(), "3")));
        assertEquals("anthropic.claude-3-haiku-20240307-v1:0", config.getModelId());
        assertNull(config.getModelRevision());
        assertEquals(9, config.getTimeoutSeconds());
        assertEquals(3, config.getMaxAttempts());
    }

    @Test
    void from_embeddedDecideRequiresDateTagAndCheckpoint() {
        ClassifyConfig config = ClassifyConfig.from(configMap(Map.of(
            ClassifyConfig.ConfigProperty.CLASSIFY_IMPLEMENTATION.name(), "embedded-decide",
            ClassifyConfig.ConfigProperty.CLASSIFY_MODEL_REVISION.name(), "261030")));
        assertEquals(ClassifyConfig.Implementation.EMBEDDED_DECIDE, config.getImplementation());
        assertEquals(ClassifyConfig.EMBEDDED_CHECKPOINT_ID, config.getModelId());
        assertEquals("261030", config.getModelRevision());
    }

    @Test
    void from_rejectsEmbeddedLlmAndRemoteDecide() {
        assertThrows(IllegalArgumentException.class, () -> ClassifyConfig.from(configMap(Map.of(
            ClassifyConfig.ConfigProperty.CLASSIFY_IMPLEMENTATION.name(), "embedded-llm"))));
        assertThrows(IllegalArgumentException.class, () -> ClassifyConfig.from(configMap(Map.of(
            ClassifyConfig.ConfigProperty.CLASSIFY_IMPLEMENTATION.name(), "remote-decide"))));
        assertThrows(IllegalArgumentException.class, () -> ClassifyConfig.from(configMap(Map.of(
            ClassifyConfig.ConfigProperty.CLASSIFY_IMPLEMENTATION.name(), "sagemaker"))));
    }

    @Test
    void from_embeddedDecideRefusesMainAndOtherCheckpoints() {
        assertThrows(IllegalArgumentException.class, () -> ClassifyConfig.from(configMap(Map.of(
            ClassifyConfig.ConfigProperty.CLASSIFY_IMPLEMENTATION.name(), "embedded-decide",
            ClassifyConfig.ConfigProperty.CLASSIFY_MODEL_REVISION.name(), "main"))));
        assertThrows(IllegalArgumentException.class, () -> ClassifyConfig.from(configMap(Map.of(
            ClassifyConfig.ConfigProperty.CLASSIFY_IMPLEMENTATION.name(), "embedded-decide"))));
        assertThrows(IllegalArgumentException.class, () -> ClassifyConfig.from(configMap(Map.of(
            ClassifyConfig.ConfigProperty.CLASSIFY_IMPLEMENTATION.name(), "embedded-decide",
            ClassifyConfig.ConfigProperty.CLASSIFY_MODEL_REVISION.name(), "261030",
            ClassifyConfig.ConfigProperty.CLASSIFY_MODEL.name(), "strands-decider-E2B-gemma4-v1-2610"))));
    }

    private static ConfigService configMap(Map<String, String> values) {
        Map<String, String> copy = new HashMap<>(values);
        return new ConfigService() {
            @Override
            public String getConfigPropertyOrError(ConfigProperty property) {
                return getConfigPropertyAsOptional(property)
                    .orElseThrow(() -> new Error("missing " + property.name()));
            }

            @Override
            public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
                return Optional.ofNullable(copy.get(property.name()));
            }
        };
    }
}
