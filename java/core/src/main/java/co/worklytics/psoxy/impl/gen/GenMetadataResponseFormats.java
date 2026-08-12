package co.worklytics.psoxy.impl.gen;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.augments.GenMetadataSchemaSupport;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import lombok.experimental.UtilityClass;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds LangChain4j {@link ResponseFormat} constraints from genMetadata {@link JsonSchemaFilter}.
 */
@UtilityClass
public class GenMetadataResponseFormats {

    public static Optional<ResponseFormat> fromOutputSchema(JsonSchemaFilter outputSchema) {
        if (outputSchema == null) {
            return Optional.empty();
        }
        Optional<GenMetadataSchemaSupport.ClassifyShape> classify =
            GenMetadataSchemaSupport.classifyShape(outputSchema);
        if (classify.isPresent()) {
            GenMetadataSchemaSupport.ClassifyShape shape = classify.get();
            JsonObjectSchema root = JsonObjectSchema.builder()
                .addProperty(shape.getPropertyName(), JsonEnumSchema.builder()
                    .enumValues(shape.getEnumValues())
                    .build())
                .required(shape.getPropertyName())
                .additionalProperties(false)
                .build();
            return Optional.of(ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder()
                    .name("genMetadataClassify")
                    .rootElement(root)
                    .build())
                .build());
        }
        JsonSchemaElement root = toElement(outputSchema);
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                .name("genMetadataExtract")
                .rootElement(root)
                .build())
            .build());
    }

    static JsonSchemaElement toElement(JsonSchemaFilter schema) {
        if (schema == null) {
            return null;
        }
        if (schema.getEnumValues() != null && !schema.getEnumValues().isEmpty()
            && (schema.isString() || schema.getType() == null)) {
            return JsonEnumSchema.builder()
                .enumValues(schema.getEnumValues())
                .build();
        }
        if (schema.isObject() || (schema.getProperties() != null && !schema.getProperties().isEmpty())) {
            Map<String, JsonSchemaElement> props = new LinkedHashMap<>();
            if (schema.getProperties() != null) {
                schema.getProperties().forEach((k, v) -> {
                    JsonSchemaElement child = toElement(v);
                    if (child != null) {
                        props.put(k, child);
                    }
                });
            }
            JsonObjectSchema.Builder b = JsonObjectSchema.builder()
                .additionalProperties(false);
            props.forEach(b::addProperty);
            if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
                b.required(schema.getRequired());
            }
            return b.build();
        }
        if (schema.isArray()) {
            JsonSchemaElement items = toElement(schema.getItems());
            JsonArraySchema.Builder b = JsonArraySchema.builder();
            if (items != null) {
                b.items(items);
            }
            return b.build();
        }
        if (schema.isString()) {
            return JsonStringSchema.builder().build();
        }
        String type = schema.getType() != null ? schema.getType().toLowerCase() : "";
        if ("number".equals(type) || "integer".equals(type)) {
            return JsonNumberSchema.builder().build();
        }
        if ("boolean".equals(type)) {
            return JsonBooleanSchema.builder().build();
        }
        return JsonStringSchema.builder().build();
    }
}
