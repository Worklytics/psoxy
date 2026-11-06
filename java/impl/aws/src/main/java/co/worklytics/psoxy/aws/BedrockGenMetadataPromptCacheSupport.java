package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.impl.gen.BedrockGenMetadataConfig;
import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import co.worklytics.psoxy.impl.gen.GenMetadataPromptAssembly;
import co.worklytics.psoxy.impl.gen.GenMetadataPromptCacheSupport;
import dev.langchain4j.model.bedrock.BedrockCachePointPlacement;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.services.bedrockruntime.model.CacheTTL;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Bedrock Converse prompt caching: static system + task/schema user prefix, dynamic input in a
 * trailing user message. Cache checkpoint after the first user message.
 */
@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class BedrockGenMetadataPromptCacheSupport implements GenMetadataPromptCacheSupport {

    @Override
    public boolean supports(GenMetadataConfig config) {
        return config instanceof BedrockGenMetadataConfig;
    }

    @Override
    public void configureRequest(GenMetadataConfig config,
                                  GenMetadataPromptAssembly assembly,
                                  ChatRequest.Builder requestBuilder) {
        requestBuilder.messages(assembly.getSplitMessages());
        requestBuilder.parameters(BedrockChatRequestParameters.builder()
            .temperature(0.0)
            .promptCaching(BedrockCachePointPlacement.AFTER_USER_MESSAGE, CacheTTL.VALUE_1_H)
            .build());
    }
}
