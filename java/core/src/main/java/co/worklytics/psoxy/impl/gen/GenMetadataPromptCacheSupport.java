package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.model.chat.request.ChatRequest;

/**
 * Platform hook: how to apply provider prompt caching to a {@link ChatRequest}.
 *
 * <p>Bound once per deployment (Bedrock vs Vertex). Per-augment / per-rule state (multiple
 * transforms, cache resource names) lives in implementation caches keyed by
 * {@link GenMetadataStaticPromptFingerprint}, not in additional DI bindings.
 */
public interface GenMetadataPromptCacheSupport {

    boolean supports(GenMetadataConfig config);

    /**
     * Sets {@link ChatRequest} messages and provider parameters. Implementations must not throw
     * for recoverable cache failures; fall back to {@link GenMetadataPromptAssembly#getUncachedMessages()}.
     */
    void configureRequest(GenMetadataConfig config,
                          GenMetadataPromptAssembly assembly,
                          ChatRequest.Builder requestBuilder);
}
