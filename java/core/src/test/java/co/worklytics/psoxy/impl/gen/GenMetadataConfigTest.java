package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.avaulta.gateway.rules.augments.GenMetadataThinkingLevels;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GenMetadataConfigTest {

    @Test
    void from_defaultsThinkingLevelToMinimal() {
        GenMetadataConfig config = GenMetadataConfig.from(configMap(Map.of(
            ProxyConfigProperty.METADATA_GEN_BACKEND.name(), "vertex")));
        assertEquals(GenMetadataThinkingLevels.MINIMAL, config.getThinkingLevel());
    }

    @Test
    void from_resolvesThinkingLevelFromEnv() {
        GenMetadataConfig config = GenMetadataConfig.from(configMap(Map.of(
            ProxyConfigProperty.METADATA_GEN_BACKEND.name(), "vertex",
            ProxyConfigProperty.METADATA_GEN_THINKING_LEVEL.name(), "high")));
        assertEquals(GenMetadataThinkingLevels.HIGH, config.getThinkingLevel());
    }

    @Test
    void from_defaultsBedrockModelToUsNovaInferenceProfile() {
        GenMetadataConfig config = GenMetadataConfig.from(configMap(Map.of(
            ProxyConfigProperty.METADATA_GEN_BACKEND.name(), "bedrock")));
        assertEquals(GenMetadataConfig.DEFAULT_BEDROCK_MODEL, config.getModelId());
        assertEquals("us.amazon.nova-2-lite-v1:0", config.getModelId());
    }

    @Test
    void from_rewritesBareNovaFoundationModelToUsInferenceProfile() {
        GenMetadataConfig config = GenMetadataConfig.from(configMap(Map.of(
            ProxyConfigProperty.METADATA_GEN_BACKEND.name(), "bedrock",
            ProxyConfigProperty.METADATA_GEN_MODEL.name(), "amazon.nova-2-lite-v1:0")));
        assertEquals("us.amazon.nova-2-lite-v1:0", config.getModelId());
    }

    @Test
    void normalizeBedrockModelId_leavesPrefixedAndNonNovaUnchanged() {
        assertEquals("us.amazon.nova-2-lite-v1:0",
            GenMetadataConfig.normalizeBedrockModelId("us.amazon.nova-2-lite-v1:0"));
        assertEquals("eu.amazon.nova-2-lite-v1:0",
            GenMetadataConfig.normalizeBedrockModelId("eu.amazon.nova-2-lite-v1:0"));
        assertEquals("anthropic.claude-3-haiku-20240307-v1:0",
            GenMetadataConfig.normalizeBedrockModelId("anthropic.claude-3-haiku-20240307-v1:0"));
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
