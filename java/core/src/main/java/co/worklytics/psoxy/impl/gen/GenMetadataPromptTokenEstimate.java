package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.List;

/**
 * Rough token estimate for genMetadata prompts ({@value #CHARS_PER_TOKEN_ESTIMATE} chars/token).
 * Not a model tokenizer; used for budget caps and provider cache eligibility heuristics.
 */
public final class GenMetadataPromptTokenEstimate {

    /** Same basis as {@link GenMetadataPromptBudget}. */
    public static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private GenMetadataPromptTokenEstimate() {
    }

    public static int estimatedTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN_ESTIMATE - 1) / CHARS_PER_TOKEN_ESTIMATE;
    }

    public static int estimatedTokens(List<ChatMessage> messages) {
        int total = 0;
        for (ChatMessage message : messages) {
            total += estimatedTokens(messageText(message));
        }
        return total;
    }

    private static String messageText(ChatMessage message) {
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.text();
        }
        if (message instanceof UserMessage userMessage) {
            return userMessage.singleText();
        }
        throw new IllegalArgumentException(
            "Unsupported message for token estimate: " + message.getClass().getSimpleName());
    }
}
