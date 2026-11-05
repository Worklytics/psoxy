package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class GenMetadataStaticPromptFingerprintTest {

    private final GenMetadataStaticPromptFingerprint fingerprint =
        new GenMetadataStaticPromptFingerprint();

    @Test
    void samePrefixMessages_sameFingerprint() {
        List<dev.langchain4j.data.message.ChatMessage> prefix = List.of(
            SystemMessage.from("sys"),
            UserMessage.from("task"));
        assertEquals(
            fingerprint.fromCachedPrefixMessages(prefix),
            fingerprint.fromCachedPrefixMessages(prefix));
    }

    @Test
    void differentStaticUser_differentFingerprint() {
        List<dev.langchain4j.data.message.ChatMessage> a = List.of(
            SystemMessage.from("sys"),
            UserMessage.from("task-a"));
        List<dev.langchain4j.data.message.ChatMessage> b = List.of(
            SystemMessage.from("sys"),
            UserMessage.from("task-b"));
        assertNotEquals(
            fingerprint.fromCachedPrefixMessages(a),
            fingerprint.fromCachedPrefixMessages(b));
    }
}
