package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataPromptTokenEstimate;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertexGenMetadataExplicitCachePolicyTest {

    @Test
    void typicalClassifyPrefix_notEligible() {
        List<dev.langchain4j.data.message.ChatMessage> prefix = List.of(
            SystemMessage.from("You are a classifier."),
            UserMessage.from("Task: classify\n\nReturn exactly one of these classes:\nA\nB\n"));
        assertFalse(VertexGenMetadataExplicitCachePolicy.prefixEligibleForExplicitCache(prefix));
    }

    @Test
    void largePrefix_eligible() {
        String big = "x".repeat(GenMetadataPromptTokenEstimate.CHARS_PER_TOKEN_ESTIMATE * 5000);
        List<dev.langchain4j.data.message.ChatMessage> prefix = List.of(
            SystemMessage.from("sys"),
            UserMessage.from(big));
        assertTrue(VertexGenMetadataExplicitCachePolicy.prefixEligibleForExplicitCache(prefix));
    }
}
