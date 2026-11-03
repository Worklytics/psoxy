package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataPromptTokenEstimate;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * Vertex AI explicit context caching minimums (see Google Cloud Gen AI cached content docs).
 *
 * <p>Typical classify / genMetadata rule prefixes are far below this; those requests use the
 * full prompt without cached content. Bedrock prompt cache has separate (lower) minimums.
 */
final class VertexGenMetadataExplicitCachePolicy {

    /**
     * Minimum cached prefix size for explicit caching on Gemini via Vertex (API returns
     * {@code INVALID_ARGUMENT} below this).
     */
    static final int MIN_PREFIX_TOKENS = 4096;

    /**
     * Slight headroom so a char-based estimate does not call create when the API would reject.
     */
    private static final double ESTIMATE_SAFETY_FACTOR = 1.1;

    private VertexGenMetadataExplicitCachePolicy() {
    }

    static boolean prefixEligibleForExplicitCache(List<ChatMessage> cachedPrefixMessages) {
        int estimated = GenMetadataPromptTokenEstimate.estimatedTokens(cachedPrefixMessages);
        return estimated >= (int) Math.ceil(MIN_PREFIX_TOKENS * ESTIMATE_SAFETY_FACTOR);
    }

    static boolean isMinimumTokenCountError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = c.getMessage();
            if (msg != null && msg.contains("minimum token count")) {
                return true;
            }
        }
        return false;
    }
}
