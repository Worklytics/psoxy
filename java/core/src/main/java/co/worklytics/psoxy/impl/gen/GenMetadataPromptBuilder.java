package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.augments.GenMetadataSchemaSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.experimental.UtilityClass;

import java.util.List;

/**
 * Builds chat messages for genMetadata cloud inference (Bedrock / Vertex).
 */
@UtilityClass
class GenMetadataPromptBuilder {

    static final String SYSTEM_CLASSIFY =
        "You are a data-processing component in a privacy proxy. "
            + "Respond with exactly one JSON object whose single property is the classification label. "
            + "Example shape: {\"category\":\"<label>\"}. "
            + "Use only an allowed label as the property value. "
            + "No markdown fences, no prose before or after the JSON.";

    static final String SYSTEM_EXTRACT =
        "You are a data-processing component in a privacy proxy. "
            + "Respond with exactly one JSON object that is an INSTANCE of the task result, "
            + "not a JSON Schema definition. "
            + "Never include schema keywords such as type, properties, required, or enum. "
            + "No markdown fences, no prose before or after the JSON.";

    static List<ChatMessage> toMessages(String taskPrompt, JsonSchemaFilter outputSchema,
                                        String inputData, ObjectMapper objectMapper) {
        if (GenMetadataSchemaSupport.mode(outputSchema) == GenMetadataSchemaSupport.Mode.CLASSIFY) {
            return List.of(
                SystemMessage.from(SYSTEM_CLASSIFY),
                UserMessage.from(classifyUserContent(taskPrompt, outputSchema, inputData))
            );
        }
        return List.of(
            SystemMessage.from(SYSTEM_EXTRACT),
            UserMessage.from(extractUserContent(taskPrompt, outputSchema, inputData, objectMapper))
        );
    }

    static String classifyUserContent(String taskPrompt, JsonSchemaFilter outputSchema,
                                      String inputData) {
        GenMetadataSchemaSupport.ClassifyShape shape =
            GenMetadataSchemaSupport.classifyShape(outputSchema).orElseThrow();
        String property = shape.getPropertyName();
        return """
            Task: %s

            Return exactly one JSON object of the form {"%s":"<label>"} where <label> is one of:
            %s

            Input data to process:
            %s
            """.formatted(
            taskPrompt.trim(),
            property,
            String.join("\n", shape.getEnumValues()),
            inputData);
    }

    static String extractUserContent(String taskPrompt, JsonSchemaFilter outputSchema,
                                     String inputData, ObjectMapper objectMapper) {
        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(outputSchema);
        } catch (Exception e) {
            schemaJson = "{}";
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
