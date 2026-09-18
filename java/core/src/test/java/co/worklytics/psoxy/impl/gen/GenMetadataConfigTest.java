package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import com.avaulta.gateway.rules.augments.GenMetadataThinkingLevels;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenMetadataConfigTest {

    @Test
    void bedrockFrom_defaultsModelToUsNovaInferenceProfile() {
        GenMetadataConfig config = BedrockGenMetadataConfig.from(configMap(Map.of()));
        assertEquals(GenMetadataConfig.Backend.BEDROCK, config.getBackend());
        assertEquals(BedrockGenMetadataConfig.DEFAULT_MODEL, config.getModelId());
        assertEquals("us.amazon.nova-2-lite-v1:0", config.getModelId());
    }

    @Test
    void vertexFrom_defaultsModelRegionAndThinkingLevel() {
        VertexGenMetadataConfig config = VertexGenMetadataConfig.from(configMap(Map.of()));
        assertEquals(VertexGenMetadataConfig.DEFAULT_MODEL, config.getModelId());
        assertEquals(VertexGenMetadataConfig.DEFAULT_MODEL_REGION, config.getModelRegion());
        assertEquals(GenMetadataThinkingLevels.MINIMAL, config.getThinkingLevel());
    }

    @Test
    void vertexFrom_resolvesThinkingLevelFromEnv() {
        VertexGenMetadataConfig config = VertexGenMetadataConfig.from(configMap(Map.of(
            VertexGenMetadataConfig.ConfigProperty.GEN_METADATA_THINKING_LEVEL.name(), "high")));
        assertEquals(GenMetadataThinkingLevels.HIGH, config.getThinkingLevel());
    }

    @Test
    void bedrockFrom_rewritesBareNovaFoundationModelToUsInferenceProfile() {
        GenMetadataConfig config = BedrockGenMetadataConfig.from(configMap(Map.of(
            GenMetadataConfig.ConfigProperty.GEN_METADATA_MODEL.name(), "amazon.nova-2-lite-v1:0")));
        assertEquals("us.amazon.nova-2-lite-v1:0", config.getModelId());
    }

    @Test
    void unknownBackendConfigValueIsNotACloudBackend() {
        assertTrue(GenMetadataConfig.Backend.fromConfigValue("local").isEmpty());
        assertFalse(new GenMetadataConfig.Unsupported("local").isSupportedCloudBackend());
    }

    @Test
    void bedrockAndVertexAreSupportedCloudBackends() {
        assertTrue(BedrockGenMetadataConfig.of("m", 5).isSupportedCloudBackend());
        assertTrue(VertexGenMetadataConfig.of("m", "global", 5).isSupportedCloudBackend());
    }

    @Test
    void normalizeModelId_leavesPrefixedAndNonNovaUnchanged() {
        assertEquals("us.amazon.nova-2-lite-v1:0",
            BedrockGenMetadataConfig.normalizeModelId("us.amazon.nova-2-lite-v1:0"));
        assertEquals("eu.amazon.nova-2-lite-v1:0",
            BedrockGenMetadataConfig.normalizeModelId("eu.amazon.nova-2-lite-v1:0"));
        assertEquals("anthropic.claude-3-haiku-20240307-v1:0",
            BedrockGenMetadataConfig.normalizeModelId("anthropic.claude-3-haiku-20240307-v1:0"));
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
