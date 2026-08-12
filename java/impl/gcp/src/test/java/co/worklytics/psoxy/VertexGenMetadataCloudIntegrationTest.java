package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

/**
 * Opt-in live Vertex smoke test. Skipped unless {@code -Pgen-metadata-cloud-integration}
 * sets {@code psoxy.genMetadata.cloudIntegration=true} and ADC can reach Vertex AI.
 */
class VertexGenMetadataCloudIntegrationTest {

    @Test
    void providerCanBuildClient() {
        Assumptions.assumeTrue(
            Boolean.parseBoolean(System.getProperty("psoxy.genMetadata.cloudIntegration", "false")),
            "Set -Pgen-metadata-cloud-integration to run live Vertex checks");
        VertexGeminiChatModelProvider provider = new VertexGeminiChatModelProvider();
        GenMetadataConfig config = GenMetadataConfig.builder()
            .backend(GenMetadataConfig.BACKEND_VERTEX)
            .modelId(GenMetadataConfig.DEFAULT_VERTEX_MODEL)
            .timeoutSeconds(30)
            .maxInputChars(1024)
            .maxTokens(32)
            .build();
        Assumptions.assumeTrue(provider.supports(config));
        // Building the client validates wiring; a chat() call would incur Vertex spend.
        provider.create(config, Path.of(System.getProperty("java.io.tmpdir")));
    }
}
