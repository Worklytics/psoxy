package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Deterministic id for the static portion of a genMetadata / classify prompt (per augment rule).
 *
 * <p>Same rule YAML on any proxy instance yields the same fingerprint. Used by cloud providers that
 * need a stable cache key (e.g. Vertex cached-content display name). Bedrock prompt cache keys
 * content on the wire and does not use this id.
 */
@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class GenMetadataStaticPromptFingerprint {

    /**
     * Fingerprint from the messages in {@link GenMetadataPromptAssembly#getCachedPrefixMessages()}.
     */
    public String fromCachedPrefixMessages(List<ChatMessage> cachedPrefixMessages) {
        StringBuilder material = new StringBuilder();
        for (ChatMessage message : cachedPrefixMessages) {
            material.append(messageType(message)).append('\0').append(messageText(message)).append('\n');
        }
        return sha256Hex(material.toString());
    }

    static String sha256Hex(String material) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String messageType(ChatMessage message) {
        if (message instanceof SystemMessage) {
            return "system";
        }
        if (message instanceof UserMessage) {
            return "user";
        }
        throw new IllegalArgumentException(
            "genMetadata cached prefix may only contain system/user messages, got "
                + message.getClass().getSimpleName());
    }

    private static String messageText(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        throw new IllegalArgumentException("Unsupported message type: " + message.getClass());
    }
}
