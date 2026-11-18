package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.impl.gen.BedrockGenMetadataConfig;
import co.worklytics.psoxy.impl.gen.GenMetadataPromptAssembly;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.request.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BedrockGenMetadataPromptCacheSupportTest {

    private final BedrockGenMetadataPromptCacheSupport support =
        new BedrockGenMetadataPromptCacheSupport();

    @Test
    void configureRequest_usesSplitMessagesAndBedrockCacheParameters() {
        UserMessage dynamic = UserMessage.from("payload");
        GenMetadataPromptAssembly assembly = GenMetadataPromptAssembly.builder()
            .uncachedMessages(List.of(UserMessage.from("full")))
            .splitMessages(List.of(
                SystemMessage.from("system"),
                UserMessage.from("static"),
                dynamic))
            .cachedPrefixMessages(List.of(SystemMessage.from("system"), UserMessage.from("static")))
            .dynamicUserMessage(dynamic)
            .build();

        ChatRequest.Builder requestBuilder = ChatRequest.builder();
        support.configureRequest(BedrockGenMetadataConfig.of("amazon.nova-lite-v1:0", 15),
            assembly, requestBuilder);
        ChatRequest request = requestBuilder.build();

        assertTrue(support.supports(BedrockGenMetadataConfig.of("m", 1)));
        assertEquals(3, request.messages().size());
        assertInstanceOf(BedrockChatRequestParameters.class, request.parameters());
        BedrockChatRequestParameters params = (BedrockChatRequestParameters) request.parameters();
        assertTrue(params.cachePointPlacement() != null);
    }
}
