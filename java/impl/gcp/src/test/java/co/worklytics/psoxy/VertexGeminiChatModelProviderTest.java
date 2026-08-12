package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        // Without FUNCTION_REGION / GOOGLE_CLOUD_REGION in the test JVM, expect default.
        assertEquals("us-central1", provider.resolveLocation());
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
