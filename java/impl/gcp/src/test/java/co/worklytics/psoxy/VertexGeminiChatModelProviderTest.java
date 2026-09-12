package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import com.avaulta.gateway.rules.augments.GenMetadataThinkingLevels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertexGeminiChatModelProviderTest {

    @Test
    void supports_onlyVertex() {
        VertexGeminiChatModelProvider provider = new VertexGeminiChatModelProvider();
        assertTrue(provider.supports(config(GenMetadataConfig.BACKEND_VERTEX, null)));
        assertFalse(provider.supports(config("local", null)));
        assertFalse(provider.supports(config(GenMetadataConfig.BACKEND_BEDROCK, null)));
    }

    @Test
    void resolveModelLocation_defaultsToGlobal() {
        VertexGeminiChatModelProvider provider = new VertexGeminiChatModelProvider();
        assertEquals(GenMetadataConfig.DEFAULT_VERTEX_MODEL_REGION,
            provider.resolveModelLocation(config(GenMetadataConfig.BACKEND_VERTEX, null)));
        assertEquals(GenMetadataConfig.DEFAULT_VERTEX_MODEL_REGION,
            provider.resolveModelLocation(config(GenMetadataConfig.BACKEND_VERTEX, "  ")));
    }

    @Test
    void resolveModelLocation_usesConfigOverride() {
        VertexGeminiChatModelProvider provider = new VertexGeminiChatModelProvider();
        assertEquals("europe-west1",
            provider.resolveModelLocation(config(GenMetadataConfig.BACKEND_VERTEX, " europe-west1 ")));
    }

    @Test
    void thinkingLevel_defaultsToMinimalConstant() {
        assertEquals(GenMetadataThinkingLevels.MINIMAL, GenMetadataThinkingLevels.DEFAULT);
    }

    @Test
    void resolveThinkingLevel_fromConfig() {
        assertEquals(GenMetadataThinkingLevels.MINIMAL,
            GenMetadataThinkingLevels.resolve(null));
        assertEquals(GenMetadataThinkingLevels.HIGH,
            GenMetadataThinkingLevels.resolve(
                GenMetadataConfig.builder()
                    .backend(GenMetadataConfig.BACKEND_VERTEX)
                    .modelId(GenMetadataConfig.DEFAULT_VERTEX_MODEL)
                    .thinkingLevel("high")
                    .timeoutSeconds(15)
                    .maxInputChars(4096)
                    .maxTokens(GenMetadataConfig.DEFAULT_MAX_TOKENS)
                    .build()
                    .getThinkingLevel()));
    }

    private static GenMetadataConfig config(String backend, String modelRegion) {
        return GenMetadataConfig.builder()
            .backend(backend)
            .modelId(GenMetadataConfig.DEFAULT_VERTEX_MODEL)
            .modelRegion(modelRegion)
            .timeoutSeconds(15)
            .maxInputChars(4096)
            .maxTokens(GenMetadataConfig.DEFAULT_MAX_TOKENS)
            .build();
    }
}
