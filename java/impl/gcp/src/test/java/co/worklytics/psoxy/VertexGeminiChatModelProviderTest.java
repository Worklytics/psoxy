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
        assertTrue(provider.supports(config(GenMetadataConfig.BACKEND_VERTEX)));
        assertFalse(provider.supports(config("local")));
        assertFalse(provider.supports(config(GenMetadataConfig.BACKEND_BEDROCK)));
    }

    @Test
    void resolveLocation_defaultsWhenUnset() {
        VertexGeminiChatModelProvider provider = new VertexGeminiChatModelProvider();
        // Without region env vars / metadata in the test JVM, expect default.
        assertEquals("us-central1", provider.resolveLocation());
    }

    @Test
    void regionNameFromMetadataPath_parsesRegionAttr() {
        assertEquals("europe-west1",
            VertexGeminiChatModelProvider.regionNameFromMetadataPath(
                "projects/123456/regions/europe-west1", "/regions/"));
        assertNull(VertexGeminiChatModelProvider.regionNameFromMetadataPath(null, "/regions/"));
    }

    @Test
    void regionFromZone_stripsZoneSuffix() {
        assertEquals("us-central1",
            VertexGeminiChatModelProvider.regionFromZone("projects/123/zones/us-central1-a"));
        assertNull(VertexGeminiChatModelProvider.regionFromZone(null));
    }

    private static GenMetadataConfig config(String backend) {
        return GenMetadataConfig.builder()
            .backend(backend)
            .modelId(GenMetadataConfig.DEFAULT_VERTEX_MODEL)
            .timeoutSeconds(15)
            .maxInputChars(4096)
            .maxTokens(256)
            .build();
    }
}
