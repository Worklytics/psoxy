package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockChatModelProviderTest {

    @Test
    void supports_onlyBedrock() {
        BedrockChatModelProvider provider = new BedrockChatModelProvider();
        assertTrue(provider.supports(config(GenMetadataConfig.BACKEND_BEDROCK)));
        assertFalse(provider.supports(config("local")));
        assertFalse(provider.supports(config(GenMetadataConfig.BACKEND_VERTEX)));
    }

    private static GenMetadataConfig config(String backend) {
        return GenMetadataConfig.builder()
            .backend(backend)
            .modelId(GenMetadataConfig.DEFAULT_BEDROCK_MODEL)
            .timeoutSeconds(15)
            .maxInputChars(4096)
            .maxTokens(256)
            .build();
    }
}
