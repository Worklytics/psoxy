package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Builds chat messages for genMetadata inference (local and cloud backends).
 */
@UtilityClass
class GenMetadataPromptBuilder {

    private static final String SYSTEM_PROMPT =
        "You are a data-processing component in a privacy proxy. "
            + "Respond with exactly one JSON value (no markdown, no prose).";

    static List<ChatMessage> toMessages(String taskPrompt, JsonSchemaFilter outputSchema,
                                        String inputData, ObjectMapper objectMapper) {
        return List.of(
            SystemMessage.from(SYSTEM_PROMPT),
            UserMessage.from(userContent(taskPrompt, outputSchema, inputData, objectMapper))
        );
    }

    static String userContent(String taskPrompt, JsonSchemaFilter outputSchema, String inputData,
                              ObjectMapper objectMapper) {
        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(outputSchema);
        } catch (Exception e) {
            schemaJson = "{}";
        }
        return """
            Task: %s

            The JSON MUST validate against this JSON Schema:
            %s

            Input data to process:
            %s
            """.formatted(taskPrompt.trim(), schemaJson, inputData);
    }
}
