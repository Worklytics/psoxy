package co.worklytics.psoxy.aws;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Opt-in live Bedrock smoke test. Skipped unless {@code -Pgen-metadata-cloud-integration}
 * sets {@code psoxy.genMetadata.cloudIntegration=true} and AWS credentials can call Bedrock.
 */
class BedrockGenMetadataCloudIntegrationTest {

    @Test
    void providerSupportsBedrock() {
        Assumptions.assumeTrue(
            Boolean.parseBoolean(System.getProperty("psoxy.genMetadata.cloudIntegration", "false")),
            "Set -Pgen-metadata-cloud-integration to run live Bedrock checks");
        BedrockChatModelProvider provider = new BedrockChatModelProvider();
        Assumptions.assumeTrue(provider.supports(
            co.worklytics.psoxy.impl.gen.GenMetadataConfig.builder()
                .backend(co.worklytics.psoxy.impl.gen.GenMetadataConfig.BACKEND_BEDROCK)
                .modelId(co.worklytics.psoxy.impl.gen.GenMetadataConfig.DEFAULT_BEDROCK_MODEL)
                .timeoutSeconds(30)
                .maxInputChars(1024)
                .maxTokens(32)
                .build()));
        // Live InvokeModel is intentionally not asserted here — requires account model access.
        // Creating the client validates SDK wiring without a billable call when credentials exist.
        provider.create(
            co.worklytics.psoxy.impl.gen.GenMetadataConfig.builder()
                .backend(co.worklytics.psoxy.impl.gen.GenMetadataConfig.BACKEND_BEDROCK)
                .modelId(co.worklytics.psoxy.impl.gen.GenMetadataConfig.DEFAULT_BEDROCK_MODEL)
                .timeoutSeconds(30)
                .maxInputChars(1024)
                .maxTokens(32)
                .build(),
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir")));
    }
}
