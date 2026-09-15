package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.impl.gen.BedrockGenMetadataConfig;
import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import co.worklytics.psoxy.impl.gen.VertexGenMetadataConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockChatModelProviderTest {

    @Test
    void supports_onlyBedrock() {
        BedrockChatModelProvider provider = new BedrockChatModelProvider();
        assertTrue(provider.supports(BedrockGenMetadataConfig.of(
            BedrockGenMetadataConfig.DEFAULT_MODEL, 15)));
        assertFalse(provider.supports(new GenMetadataConfig.Unsupported("local")));
        assertFalse(provider.supports(VertexGenMetadataConfig.of(
            VertexGenMetadataConfig.DEFAULT_MODEL, "global", 15)));
    }
}
