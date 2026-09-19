package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchema;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Builds chat messages for genMetadata / classify cloud inference (Bedrock / Vertex).
 */
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
class GenMetadataPromptBuilder {

    final ObjectMapper objectMapper;

    static final String SYSTEM_CLASSIFY =
        "You are a data-processing component in a privacy proxy. "
            + "Respond with exactly one allowed class. "
            + "The class name must appear in your reply as an exact substring. "
            + "No markdown fences.";

    static final String SYSTEM_COMPUTE =
        "You are a data-processing component in a privacy proxy. "
            + "Respond with exactly one JSON object that is an INSTANCE of the task result, "
            + "not a JSON Schema definition. "
            + "Never include schema keywords such as type, properties, required, or enum. "
            + "No markdown fences, no prose before or after the JSON.";

    List<ChatMessage> toMessages(String taskPrompt, JsonSchema outputSchema,
                                  String inputData) {
        return List.of(
            SystemMessage.from(SYSTEM_COMPUTE),
            UserMessage.from(computeUserContent(taskPrompt, outputSchema, inputData))
        );
    }

    List<ChatMessage> toClassifyMessages(String taskPrompt, List<String> classes,
                                         String inputData) {
        return List.of(
            SystemMessage.from(SYSTEM_CLASSIFY),
            UserMessage.from(classifyUserContent(taskPrompt, classes, inputData))
        );
    }

    String classifyUserContent(String taskPrompt, List<String> classes, String inputData) {
        StringBuilder labels = new StringBuilder();
        if (classes != null) {
            for (String value : classes) {
                if (StringUtils.isBlank(value)) {
                    continue;
                }
                labels.append(value).append('\n');
            }
        }
        return """
            Task: %s

            Return exactly one of these classes:
            %s
            Input data to process:
            %s
            """.formatted(taskPrompt.trim(), labels, inputData);
    }

    String computeUserContent(String taskPrompt, JsonSchema outputSchema,
                               String inputData) {
        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(outputSchema);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize genMetadata outputSchema", e);
        }
        return """
            Task: %s

            Return a JSON object that validates against this schema (instance, not the schema):
            %s

            Input data to process:
            %s
            """.formatted(taskPrompt.trim(), schemaJson, inputData);
    }
}
