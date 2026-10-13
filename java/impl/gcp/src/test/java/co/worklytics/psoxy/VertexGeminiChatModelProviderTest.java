package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.BedrockGenMetadataConfig;
import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import co.worklytics.psoxy.impl.gen.VertexGenMetadataConfig;
import com.avaulta.gateway.rules.augments.GenMetadataThinkingLevels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertexGeminiChatModelProviderTest {

    @Test
    void supports_onlyVertex() {
        VertexGeminiChatModelProvider provider = new VertexGeminiChatModelProvider();
        assertTrue(provider.supports(VertexGenMetadataConfig.of(
            VertexGenMetadataConfig.DEFAULT_MODEL, null, 15)));
        assertFalse(provider.supports(new GenMetadataConfig.Unsupported("local")));
        assertFalse(provider.supports(BedrockGenMetadataConfig.of(
            BedrockGenMetadataConfig.DEFAULT_MODEL, 15)));
    }

    @Test
    void of_blankRegionDefaultsToGlobal() {
        assertEquals(VertexGenMetadataConfig.DEFAULT_MODEL_REGION,
            VertexGenMetadataConfig.of(VertexGenMetadataConfig.DEFAULT_MODEL, null, 15).getModelRegion());
        assertEquals(VertexGenMetadataConfig.DEFAULT_MODEL_REGION,
            VertexGenMetadataConfig.of(VertexGenMetadataConfig.DEFAULT_MODEL, "  ", 15).getModelRegion());
    }

    @Test
    void of_usesRegionOverride() {
        assertEquals("europe-west1",
            VertexGenMetadataConfig.of(VertexGenMetadataConfig.DEFAULT_MODEL, " europe-west1 ", 15)
                .getModelRegion());
    }

    @Test
    void thinkingLevel_defaultsToMinimalConstant() {
        assertEquals(GenMetadataThinkingLevels.MINIMAL, GenMetadataThinkingLevels.DEFAULT);
    }

    @Test
    void resolveThinkingLevel_fromConfig() {
        assertEquals(GenMetadataThinkingLevels.HIGH,
            VertexGenMetadataConfig.of(
                VertexGenMetadataConfig.DEFAULT_MODEL, "global", "high", 15)
                .getThinkingLevel());
    }
}
