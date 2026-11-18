package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.data.message.UserMessage;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class GenMetadataBackendTestSupport {

    static GenMetadataPromptCacheSupportFactory noPromptCacheFactory() {
        return new GenMetadataPromptCacheSupportFactory(
            Set.of(), new NoOpGenMetadataPromptCacheSupport());
    }

    static GenMetadataPromptAssembly simpleAssembly(String userText) {
        UserMessage user = UserMessage.from(userText);
        List<dev.langchain4j.data.message.ChatMessage> messages = List.of(user);
        return GenMetadataPromptAssembly.builder()
            .uncachedMessages(messages)
            .splitMessages(messages)
            .cachedPrefixMessages(messages)
            .dynamicUserMessage(user)
            .build();
    }
}
