package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchema;
import com.avaulta.gateway.rules.augments.GenMetadataSchemaSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.AllArgsConstructor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * Builds chat messages for genMetadata cloud inference (Bedrock / Vertex).
 */
@Singleton
@AllArgsConstructor(onConstructor_ = @Inject)
class GenMetadataPromptBuilder {

    final ObjectMapper objectMapper;

    static final String SYSTEM_CLASSIFY =
        "You are a data-processing component in a privacy proxy. "
            + "Respond with exactly one JSON object whose single property is the classification label. "
            + "Example shape: {\"category\":\"<label>\"}. "
            + "Use only an allowed label as the property value. "
            + "No markdown fences, no prose before or after the JSON.";

    static final String SYSTEM_CLASSIFY_STRING =
        "You are a data-processing component in a privacy proxy. "
            + "Respond with exactly one allowed classification label as a JSON string. "
            + "Do not wrap the label in an object or add any other keys. "
            + "No markdown fences, no prose before or after the string.";

    static final String SYSTEM_COMPUTE =
        "You are a data-processing component in a privacy proxy. "
            + "Respond with exactly one JSON object that is an INSTANCE of the task result, "
            + "not a JSON Schema definition. "
            + "Never include schema keywords such as type, properties, required, or enum. "
            + "No markdown fences, no prose before or after the JSON.";

    List<ChatMessage> toMessages(String taskPrompt, JsonSchema outputSchema,
                                  String inputData) {
        if (GenMetadataSchemaSupport.mode(outputSchema) == GenMetadataSchemaSupport.Mode.CLASSIFY) {
            GenMetadataSchemaSupport.ClassifyShape shape =
                GenMetadataSchemaSupport.classifyShape(outputSchema).orElseThrow();
            String system = shape.isRootString() ? SYSTEM_CLASSIFY_STRING : SYSTEM_CLASSIFY;
            return List.of(
                SystemMessage.from(system),
                UserMessage.from(classifyUserContent(taskPrompt, outputSchema, inputData))
            );
        }
        return List.of(
            SystemMessage.from(SYSTEM_COMPUTE),
            UserMessage.from(computeUserContent(taskPrompt, outputSchema, inputData))
        );
    }

    String classifyUserContent(String taskPrompt, JsonSchema outputSchema,
                               String inputData) {
        GenMetadataSchemaSupport.ClassifyShape shape =
            GenMetadataSchemaSupport.classifyShape(outputSchema).orElseThrow();
        String labels = String.join("\n", shape.getEnumValues());
        if (shape.isRootString()) {
            return """
                Task: %s

                Return exactly one of these labels as a JSON string:
                %s

                Input data to process:
                %s
                """.formatted(taskPrompt.trim(), labels, inputData);
        }
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
            labels,
            inputData);
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
