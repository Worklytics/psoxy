package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    void resolveApiEndpoint_overridesForGlobalOnly() {
        assertEquals(VertexGeminiChatModelProvider.GLOBAL_API_ENDPOINT,
            VertexGeminiChatModelProvider.resolveApiEndpoint("global"));
        assertEquals(VertexGeminiChatModelProvider.GLOBAL_API_ENDPOINT,
            VertexGeminiChatModelProvider.resolveApiEndpoint("GLOBAL"));
        // Multi-region location strings are not valid for this Java client host pattern;
        // do not treat them as a supported default — leave SDK host construction alone.
        assertNull(VertexGeminiChatModelProvider.resolveApiEndpoint("us"));
        assertNull(VertexGeminiChatModelProvider.resolveApiEndpoint("us-central1"));
        assertNull(VertexGeminiChatModelProvider.resolveApiEndpoint(null));
    }

    private static GenMetadataConfig config(String backend, String modelRegion) {
        return GenMetadataConfig.builder()
            .backend(backend)
            .modelId(GenMetadataConfig.DEFAULT_VERTEX_MODEL)
            .modelRegion(modelRegion)
            .timeoutSeconds(15)
            .maxInputChars(4096)
            .maxTokens(256)
            .build();
    }
}
