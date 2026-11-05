package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataStaticPromptFingerprint;
import co.worklytics.psoxy.impl.gen.VertexGenMetadataConfig;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.genai.types.CachedContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.google.genai.GoogleGenAiCaches;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

/**
 * Resolves Vertex cached-content resource names for genMetadata static prefixes.
 *
 * <p>Process-local {@link LoadingCache} avoids repeated list/create API calls. Keys are
 * deterministic from the augment rule prefix ({@link GenMetadataStaticPromptFingerprint}) so
 * concurrent Cloud Function instances reuse the same Vertex resource via a shared display name.
 */
@Log
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VertexGenMetadataCachedContentResolver {

    private static final Duration LOCAL_CACHE_TTL = Duration.ofMinutes(50);

    private final VertexGenAiClientFactory clientFactory;
    private final GenMetadataStaticPromptFingerprint staticPromptFingerprint;

    private final LoadingCache<CacheKey, String> resourceNames = CacheBuilder.newBuilder()
        .expireAfterWrite(LOCAL_CACHE_TTL)
        .build(new CacheLoader<>() {
            @Override
            public String load(CacheKey key) {
                return resolveResourceName(key);
            }
        });

    private final LoadingCache<String, VertexGenAiClientFactory.VertexGenAiCachesContext> clients =
        CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .build(new CacheLoader<>() {
                @Override
                public VertexGenAiClientFactory.VertexGenAiCachesContext load(String location) {
                    return clientFactory.cachesContext(location);
                }
            });

    public String cachedContentResourceName(VertexGenMetadataConfig config,
                                            List<ChatMessage> cachedPrefixMessages) {
        String fingerprint = staticPromptFingerprint.fromCachedPrefixMessages(cachedPrefixMessages);
        CacheKey key = new CacheKey(
            clientFactory.projectId(),
            config.getModelRegion(),
            config.getModelId(),
            fingerprint,
            cachedPrefixMessages);
        try {
            return resourceNames.get(key);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Vertex cached content resolution failed", cause);
        }
    }

    private String resolveResourceName(CacheKey key) {
        VertexGenAiClientFactory.VertexGenAiCachesContext ctx = clientContext(key.getLocation());
        GoogleGenAiCaches caches = ctx.getCaches();
        String displayName = clientFactory.cacheDisplayName(key.getStaticPromptFingerprint());

        Optional<CachedContent> existing = clientFactory.findByDisplayName(caches, displayName);
        if (existing.isPresent()) {
            return existing.get().name().orElseThrow(
                () -> new IllegalStateException("Vertex cached content missing resource name"));
        }

        try {
            CachedContent created = clientFactory.createCachedContent(
                ctx.getClient(),
                key.getModelId(),
                key.getPrefixMessages(),
                displayName,
                VertexGenAiClientFactory.PROMPT_CACHE_TTL);
            return created.name().orElseThrow(
                () -> new IllegalStateException("Vertex cached content missing resource name"));
        } catch (RuntimeException createFailed) {
            Optional<CachedContent> raced = clientFactory.findByDisplayName(caches, displayName);
            if (raced.isPresent()) {
                log.log(Level.FINE, "Vertex cached content create raced; reusing displayName="
                    + displayName);
                return raced.get().name().orElseThrow(
                    () -> new IllegalStateException("Vertex cached content missing resource name"));
            }
            throw createFailed;
        }
    }

    private VertexGenAiClientFactory.VertexGenAiCachesContext clientContext(String location) {
        try {
            return clients.get(location);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to init Vertex genAI client", e.getCause());
        }
    }

    @Value
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    static class CacheKey {
        @EqualsAndHashCode.Include
        String projectId;
        @EqualsAndHashCode.Include
        String location;
        @EqualsAndHashCode.Include
        String modelId;
        @EqualsAndHashCode.Include
        String staticPromptFingerprint;
        List<ChatMessage> prefixMessages;
    }
}
