
package com.avaulta.gateway.rules;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.avaulta.gateway.rules.JsonSchema.StringFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class JsonSchemaValidationUtils {


    final ObjectMapper objectMapper;
    final JsonMetaSchema metaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV202012())
            // .format(PatternFormat.of("pseudonym",
            // "^p~[a-zA-Z0-9_-]{43,}$",
            // null))
            .build();
    final JsonSchemaFactory jsonSchemaFactory = new JsonSchemaFactory.Builder()
            .defaultMetaSchemaIri(metaSchema.getIri())
            .metaSchema(metaSchema)
            .build();

    @SneakyThrows
    public boolean validateJsonBySchema(String jsonString,
            com.avaulta.gateway.rules.JsonSchema schema) {
        JsonNode deserialized = objectMapper.readTree(jsonString);

        // TODO: cache this??? in practice, each instance is only going to have 3-4 of them. so why
        // the object conversion EVERY time?

        // TODO: remove this hack; rewritePseudonymToPattern is terrible hack, due to above attempt
        // to register custom format not working
        JsonNode schemaNode = objectMapper.valueToTree(rewritePseudonymToPattern(schema));
        JsonSchema jsonSchema = jsonSchemaFactory.getSchema(schemaNode);

        Set<ValidationMessage> validationMessages = jsonSchema.validate(deserialized);
        return validationMessages.isEmpty();
    }

    com.avaulta.gateway.rules.JsonSchema rewritePseudonymToPattern(
            com.avaulta.gateway.rules.JsonSchema schema) {
        if (schema.getFormat() != null
                && schema.getFormat().equals(StringFormat.PSEUDONYM.getStringEncoding())) {
            return schema.toBuilder().format(null).pattern("^p~[a-zA-Z0-9_-]{43,}$").build();
        } else if (schema.getItems() != null) {
            return schema.toBuilder().items(rewritePseudonymToPattern(schema.getItems())).build();
        } else if (schema.getProperties() != null) {
            return schema.toBuilder()
                    .properties(schema.getProperties().entrySet().stream().collect(Collectors.toMap(
                            Map.Entry::getKey, e -> rewritePseudonymToPattern(e.getValue()))))
                    .build();
        } else {
            return schema;
        }
    }
}
