package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataStaticPromptFingerprint;
import co.worklytics.psoxy.impl.gen.VertexGenMetadataConfig;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.genai.types.CachedContent;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.google.genai.GoogleGenAiCaches;
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
public class VertexGenMetadataCachedContentResolver {

    private static final Duration LOCAL_CACHE_TTL = Duration.ofMinutes(50);

    private final LoadingCache<CacheKey, String> resourceNames = CacheBuilder.newBuilder()
        .expireAfterWrite(LOCAL_CACHE_TTL)
        .build(new CacheLoader<>() {
            @Override
            public String load(CacheKey key) {
                return resolveResourceName(key);
            }
        });

    private final LoadingCache<String, VertexGenAiClientSupport.VertexGenAiCachesContext> clients =
        CacheBuilder.newBuilder()
            .expireAfterAccess(Duration.ofHours(1))
            .build(new CacheLoader<>() {
                @Override
                public VertexGenAiClientSupport.VertexGenAiCachesContext load(String endpointKey) {
                    String[] parts = endpointKey.split("\\|", 2);
                    return VertexGenAiClientSupport.cachesContext(parts[0], parts[1]);
                }
            });

    @Inject
    public VertexGenMetadataCachedContentResolver() {
    }

    public String cachedContentResourceName(VertexGenMetadataConfig config,
                                            List<ChatMessage> cachedPrefixMessages) {
        String fingerprint =
            GenMetadataStaticPromptFingerprint.fromCachedPrefixMessages(cachedPrefixMessages);
        CacheKey key = new CacheKey(
            VertexGenAiClientSupport.resolveProjectId(),
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
        VertexGenAiClientSupport.VertexGenAiCachesContext ctx =
            clientContext(key.projectId(), key.location());
        GoogleGenAiCaches caches = ctx.caches();
        String displayName = VertexGenAiClientSupport.cacheDisplayName(key.staticPromptFingerprint());

        Optional<CachedContent> existing =
            VertexGenAiClientSupport.findByDisplayName(caches, displayName);
        if (existing.isPresent()) {
            return existing.get().name().orElseThrow(
                () -> new IllegalStateException("Vertex cached content missing resource name"));
        }

        try {
            CachedContent created = VertexGenAiClientSupport.createCachedContent(
                ctx.client(),
                key.modelId(),
                key.prefixMessages(),
                displayName,
                VertexGenAiClientSupport.PROMPT_CACHE_TTL);
            return created.name().orElseThrow(
                () -> new IllegalStateException("Vertex cached content missing resource name"));
        } catch (RuntimeException createFailed) {
            Optional<CachedContent> raced =
                VertexGenAiClientSupport.findByDisplayName(caches, displayName);
            if (raced.isPresent()) {
                log.log(Level.FINE, "Vertex cached content create raced; reusing displayName="
                    + displayName);
                return raced.get().name().orElseThrow(
                    () -> new IllegalStateException("Vertex cached content missing resource name"));
            }
            throw createFailed;
        }
    }

    private VertexGenAiClientSupport.VertexGenAiCachesContext clientContext(String projectId,
                                                                            String location) {
        try {
            return clients.get(projectId + '|' + location);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to init Vertex genAI client", e.getCause());
        }
    }

    static final class CacheKey {
        private final String projectId;
        private final String location;
        private final String modelId;
        private final String staticPromptFingerprint;
        private final List<ChatMessage> prefixMessages;

        CacheKey(String projectId, String location, String modelId,
                 String staticPromptFingerprint, List<ChatMessage> prefixMessages) {
            this.projectId = projectId;
            this.location = location;
            this.modelId = modelId;
            this.staticPromptFingerprint = staticPromptFingerprint;
            this.prefixMessages = prefixMessages;
        }

        String projectId() {
            return projectId;
        }

        String location() {
            return location;
        }

        String modelId() {
            return modelId;
        }

        String staticPromptFingerprint() {
            return staticPromptFingerprint;
        }

        List<ChatMessage> prefixMessages() {
            return prefixMessages;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CacheKey cacheKey)) {
                return false;
            }
            return projectId.equals(cacheKey.projectId)
                && location.equals(cacheKey.location)
                && modelId.equals(cacheKey.modelId)
                && staticPromptFingerprint.equals(cacheKey.staticPromptFingerprint);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(projectId, location, modelId, staticPromptFingerprint);
        }
    }
}
