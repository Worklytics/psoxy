package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import co.worklytics.psoxy.impl.gen.GenMetadataPromptAssembly;
import co.worklytics.psoxy.impl.gen.GenMetadataPromptCacheSupport;
import co.worklytics.psoxy.impl.gen.VertexGenMetadataConfig;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.google.genai.GoogleGenAiChatRequestParameters;
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
public class VertexGenMetadataPromptCacheSupport implements GenMetadataPromptCacheSupport {

    private final VertexGenMetadataCachedContentResolver cachedContentResolver;

    @Inject
    public VertexGenMetadataPromptCacheSupport(VertexGenMetadataCachedContentResolver cachedContentResolver) {
        this.cachedContentResolver = cachedContentResolver;
    }

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
            return;
        }
        try {
            String cacheName = cachedContentResolver.cachedContentResourceName(
                vertex, assembly.getCachedPrefixMessages());
            requestBuilder.messages(List.of(assembly.getDynamicUserMessage()));
            requestBuilder.parameters(GoogleGenAiChatRequestParameters.builder()
                .cachedContent(cacheName)
                .build());
        } catch (RuntimeException e) {
            log.log(Level.WARNING,
                "Vertex genMetadata prompt cache unavailable; using full prompt modelId="
                    + vertex.getModelId(),
                e);
            requestBuilder.messages(assembly.getUncachedMessages());
        }
    }
}
