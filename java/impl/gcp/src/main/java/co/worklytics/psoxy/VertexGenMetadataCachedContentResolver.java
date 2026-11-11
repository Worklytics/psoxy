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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

    /**
     * Shorter than Vertex {@link VertexGenAiClientFactory#PROMPT_CACHE_TTL}; entries re-validate remote
     * {@code expireTime} on read.
     */
    private static final Duration LOCAL_CACHE_TTL = Duration.ofMinutes(40);

    private final VertexGenAiClientFactory clientFactory;
    private final GenMetadataStaticPromptFingerprint staticPromptFingerprint;
    private final Clock clock;

    private final LoadingCache<CacheKey, ResolvedCache> resourceNames = CacheBuilder.newBuilder()
        .expireAfterWrite(LOCAL_CACHE_TTL)
        .build(new CacheLoader<>() {
            @Override
            public ResolvedCache load(CacheKey key) {
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
            ResolvedCache resolved = resourceNames.get(key);
            if (!resolved.isUsable(clock)) {
                resourceNames.invalidate(key);
                resolved = resourceNames.get(key);
            }
            return resolved.getResourceName();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("Vertex cached content resolution failed", cause);
        }
    }

    private ResolvedCache resolveResourceName(CacheKey key) {
        VertexGenAiClientFactory.VertexGenAiCachesContext ctx = clientContext(key.getLocation());
        GoogleGenAiCaches caches = ctx.getCaches();
        String displayName =
            clientFactory.cacheDisplayName(key.getModelId(), key.getStaticPromptFingerprint());

        Optional<CachedContent> existing = clientFactory.findByDisplayName(caches, displayName);
        if (existing.isPresent()
            && clientFactory.isReusableCachedContent(existing.get(), key.getModelId(), clock)) {
            CachedContent content = existing.get();
            return new ResolvedCache(
                content.name().orElseThrow(
                    () -> new IllegalStateException("Vertex cached content missing resource name")),
                clientFactory.remoteExpireTime(content).orElse(null));
        }

        try {
            CachedContent created = clientFactory.createCachedContent(
                ctx.getClient(),
                key.getModelId(),
                key.getPrefixMessages(),
                displayName,
                VertexGenAiClientFactory.PROMPT_CACHE_TTL);
            return new ResolvedCache(
                created.name().orElseThrow(
                    () -> new IllegalStateException("Vertex cached content missing resource name")),
                clientFactory.remoteExpireTime(created).orElse(null));
        } catch (RuntimeException createFailed) {
            Optional<CachedContent> raced = clientFactory.findByDisplayName(caches, displayName);
            if (raced.isPresent()
                && clientFactory.isReusableCachedContent(raced.get(), key.getModelId(), clock)) {
                log.log(Level.FINE, "Vertex cached content create raced; reusing displayName="
                    + displayName);
                CachedContent content = raced.get();
                return new ResolvedCache(
                    content.name().orElseThrow(
                        () -> new IllegalStateException("Vertex cached content missing resource name")),
                    clientFactory.remoteExpireTime(content).orElse(null));
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
    static class ResolvedCache {
        String resourceName;
        Instant remoteExpireTime;

        boolean isUsable(Clock clock) {
            if (remoteExpireTime == null) {
                return true;
            }
            return remoteExpireTime.isAfter(
                clock.instant().plus(VertexGenAiClientFactory.CACHED_CONTENT_REUSE_MARGIN));
        }
    }

    @Value
    @EqualsAndHashCode(of = {"projectId", "location", "modelId", "staticPromptFingerprint"})
    static class CacheKey {
        String projectId;
        String location;
        String modelId;
        String staticPromptFingerprint;
        List<ChatMessage> prefixMessages;
    }
}
