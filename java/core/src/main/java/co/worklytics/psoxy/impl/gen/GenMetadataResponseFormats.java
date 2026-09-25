package co.worklytics.psoxy.impl.gen;

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
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds LangChain4j {@link ResponseFormat} constraints from genMetadata
 * {@link com.avaulta.gateway.rules.JsonSchema} or classify {@code classes}.
 */
@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class GenMetadataResponseFormats {

    public Optional<ResponseFormat> fromClasses(List<String> classes) {
        if (classes == null || classes.isEmpty()) {
            return Optional.empty();
        }
        List<String> values = new ArrayList<>();
        for (String value : classes) {
            if (StringUtils.isNotBlank(value)) {
                values.add(value);
            }
        }
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ResponseFormat.builder()
            .type(ResponseFormatType.JSON)
            .jsonSchema(JsonSchema.builder()
                .name("classify")
                .rootElement(JsonEnumSchema.builder()
                    .enumValues(values)
                    .build())
                .build())
            .build());
    }

    public Optional<ResponseFormat> fromOutputSchema(com.avaulta.gateway.rules.JsonSchema outputSchema) {
        if (outputSchema == null) {
            return Optional.empty();
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

    JsonSchemaElement toElement(com.avaulta.gateway.rules.JsonSchema schema) {
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
                .additionalProperties(schema.allowsAdditionalProperties());
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
