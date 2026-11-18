package co.worklytics.psoxy;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertexGenMetadataExplicitCachePolicyTest {

    private final VertexGenMetadataExplicitCachePolicy policy =
        new VertexGenMetadataExplicitCachePolicy();

    @Test
    void typicalClassifyPrefix_notEligible() {
        List<dev.langchain4j.data.message.ChatMessage> prefix = List.of(
            SystemMessage.from("You are a classifier."),
            UserMessage.from("Task: classify\n\nReturn exactly one of these classes:\nA\nB\n"));
        assertFalse(policy.prefixEligibleForExplicitCache(prefix));
    }

    @Test
    void largePrefix_eligible() {
        String big = "x".repeat(VertexGenMetadataExplicitCachePolicy.MIN_PREFIX_CHARS_TO_ATTEMPT_VERTEX_CACHE);
        List<dev.langchain4j.data.message.ChatMessage> prefix = List.of(
            SystemMessage.from("sys"),
            UserMessage.from(big));
        assertTrue(policy.prefixEligibleForExplicitCache(prefix));
    }
}
