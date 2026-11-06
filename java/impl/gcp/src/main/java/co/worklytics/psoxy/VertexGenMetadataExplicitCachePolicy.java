package co.worklytics.psoxy;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Vertex AI explicit context caching minimums (see Google Cloud Gen AI cached content docs).
 *
 * <p>Typical classify / genMetadata rule prefixes are far below this; those requests use the
 * full prompt without cached content. Bedrock prompt cache has separate (lower) minimums.
 */
@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class VertexGenMetadataExplicitCachePolicy {

    /** Vertex minimum prefix size in tokens for explicit caching. */
    static final int MIN_PREFIX_TOKENS = 4096;

    /** Avg chars per token (word + space) for eligibility checks. */
    static final int CHARS_PER_TOKEN_ESTIMATE = 5;

    /**
     * Precomputed {@code floor(MIN_PREFIX_TOKENS / 1.1) * CHARS_PER_TOKEN_ESTIMATE} so we do not
     * call create when the API would reject the prefix.
     */
    static final int MIN_PREFIX_CHARS_TO_ATTEMPT_VERTEX_CACHE = 18_618;

    public boolean prefixEligibleForExplicitCache(List<ChatMessage> cachedPrefixMessages) {
        return prefixCharCount(cachedPrefixMessages) >= MIN_PREFIX_CHARS_TO_ATTEMPT_VERTEX_CACHE;
    }

    public boolean isMinimumTokenCountError(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            String msg = c.getMessage();
            if (msg != null && msg.contains("minimum token count")) {
                return true;
            }
        }
        return false;
    }

    private static int prefixCharCount(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            if (message instanceof SystemMessage systemMessage) {
                total += systemMessage.text().length();
            } else if (message instanceof UserMessage userMessage) {
                total += userMessage.singleText().length();
            } else {
                throw new IllegalArgumentException(
                    "Unsupported cached prefix message: " + message.getClass().getSimpleName());
            }
        }
        return total;
    }
}
