package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;

/**
 * Provider-neutral prompt layout: stable prefix vs per-record dynamic input.
 *
 * <ul>
 *   <li>{@link #uncachedMessages()} — full prompt when caching is off or fails</li>
 *   <li>{@link #splitMessages()} — system + static user + dynamic user (Bedrock prompt cache)</li>
 *   <li>{@link #cachedPrefixMessages()} + {@link #dynamicUserMessage()} — prefix pinned in a
 *       provider cache; per-request tail is dynamic only</li>
 * </ul>
 *
 * <p>Stable id for a rule's prefix: {@link GenMetadataStaticPromptFingerprint#fromCachedPrefixMessages(List)}.
 */
@Value
@Builder
public class GenMetadataPromptAssembly {

    @NonNull
    List<ChatMessage> uncachedMessages;

    /** System + static user content; trailing user message holds dynamic input. */
    @NonNull
    List<ChatMessage> splitMessages;

    /** Messages to store in a provider-side context cache (excludes dynamic input). */
    @NonNull
    List<ChatMessage> cachedPrefixMessages;

    @NonNull
    UserMessage dynamicUserMessage;
}
