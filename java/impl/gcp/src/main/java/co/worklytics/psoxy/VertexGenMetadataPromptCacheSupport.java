package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import co.worklytics.psoxy.impl.gen.GenMetadataPromptAssembly;
import co.worklytics.psoxy.impl.gen.GenMetadataPromptCacheSupport;
import co.worklytics.psoxy.impl.gen.VertexGenMetadataConfig;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.google.genai.GoogleGenAiChatRequestParameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.logging.Level;

/**
 * Vertex AI context caching for the stable genMetadata / classify prefix (system + task/schema).
 */
@Log
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VertexGenMetadataPromptCacheSupport implements GenMetadataPromptCacheSupport {

    private final VertexGenMetadataCachedContentResolver cachedContentResolver;
    private final VertexGenMetadataExplicitCachePolicy explicitCachePolicy;

    @Override
    public boolean supports(GenMetadataConfig config) {
        return config instanceof VertexGenMetadataConfig;
    }

    @Override
    public void configureRequest(GenMetadataConfig config,
                                  GenMetadataPromptAssembly assembly,
                                  ChatRequest.Builder requestBuilder) {
        if (!(config instanceof VertexGenMetadataConfig vertex)) {
            requestBuilder.messages(assembly.getUncachedMessages());
        } else if (!explicitCachePolicy.prefixEligibleForExplicitCache(
            assembly.getCachedPrefixMessages())) {
            requestBuilder.messages(assembly.getUncachedMessages());
        } else {
            try {
                String cacheName = cachedContentResolver.cachedContentResourceName(
                    vertex, assembly.getCachedPrefixMessages());
                requestBuilder.messages(List.of(assembly.getDynamicUserMessage()));
                requestBuilder.parameters(GoogleGenAiChatRequestParameters.builder()
                    .cachedContent(cacheName)
                    .build());
            } catch (RuntimeException e) {
                Level level = explicitCachePolicy.isMinimumTokenCountError(e)
                    ? Level.FINE
                    : Level.WARNING;
                log.log(level,
                    "Vertex genMetadata prompt cache unavailable; using full prompt modelId="
                        + vertex.getModelId()
                        + (level == Level.FINE ? " (prefix below explicit cache minimum)" : ""),
                    level == Level.FINE ? null : e);
                requestBuilder.messages(assembly.getUncachedMessages());
            }
        }
    }
}
