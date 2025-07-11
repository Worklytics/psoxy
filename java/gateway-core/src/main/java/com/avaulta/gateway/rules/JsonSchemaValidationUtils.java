
package com.avaulta.gateway.rules;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.WWWFormCodec;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.JsonSchema.StringFormat;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

@Log
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

    private final LoadingCache<com.avaulta.gateway.rules.JsonSchema, JsonSchema> jsonSchemaCache =
            CacheBuilder.newBuilder()
                    .maximumSize(100)
                    .build(CacheLoader.from(this::getJsonSchema));

    @SneakyThrows
    public boolean validateJsonBySchema(String jsonString,
            com.avaulta.gateway.rules.JsonSchema schema) {
        JsonNode deserialized = objectMapper.readTree(jsonString);



        JsonSchema jsonSchema = jsonSchemaCache.get(schema);
        Set<ValidationMessage> validationMessages = jsonSchema.validate(deserialized);

        if (!validationMessages.isEmpty()) {
            log.warning("Validation failed for JSON request body: " + validationMessages);
        }

        return validationMessages.isEmpty();
    }


    /**
     * EXTREMELY alpha; not sure this is a good idea.
     *
     * everything form-urlencoded is a string
     * 
     * validate a form-urlencoded request body against a JSON schema
     * 
     * obviously, schema - if defined - MUST of type object
     * 
     * 
     * 
     * @param requestBody
     * @param schema
     * @return
     */
    @SneakyThrows
    public boolean validateFormUrlEncodedBySchema(String requestBody,
            com.avaulta.gateway.rules.JsonSchema schema) {
        if (!Objects.equals(schema.getType(), "object")) {
            throw new IllegalArgumentException(
                    "trying to validate form-urlencoded request body against a non-object schema");
        }


        if (schema.getProperties() != null && schema.getProperties().values().stream()
                .anyMatch(p -> !Objects.equals(p.getType(), "string"))) {
            log.warning(
                    "schema being used to validate form-urlencoded request has non-string properties; this is probably not correct; use a string with a format or pattern, if you want more validation");
        }


        // TODO: don't do this, bc won't deal with repeated fields, which are legal in
        // form-urlencoded but non-sensical with JSON objects
        // options:
        // - explicitly blow up on repeated fields in request body, if any
        // - write custom validation logic that deals checks ALL instances of a given field name
        // against it's schema in the root properties map

        // parse requestBody into a map of String to String, assuming it's form-urlencoded
        Map<String, String> map = parseFormUrlEncoded(requestBody);

        JsonSchema jsonSchema = jsonSchemaCache.get(schema);
        Set<ValidationMessage> validationMessages =
                jsonSchema.validate(objectMapper.valueToTree(map));

        if (!validationMessages.isEmpty()) {
            log.warning(
                    "Validation failed for form-urlencoded request body: " + validationMessages);
        }

        return validationMessages.isEmpty();
    }

    /**
     * Parses form-urlencoded data using Apache HTTP Client's WWWFormCodec.
     * This is the recommended approach instead of the deprecated URLEncodedUtils.parse().
     * 
     * @param formUrlEncodedString The form-urlencoded string to parse
     * @return A Map containing the parsed key-value pairs
     */
    private Map<String, String> parseFormUrlEncoded(String formUrlEncodedString) {
        if (formUrlEncodedString == null || formUrlEncodedString.trim().isEmpty()) {
            return new HashMap<>();
        }

        List<NameValuePair> pairs =
                WWWFormCodec.parse(formUrlEncodedString, StandardCharsets.UTF_8);
        Map<String, String> result = new HashMap<>();

        for (NameValuePair pair : pairs) {
            result.put(pair.getName(), pair.getValue() != null ? pair.getValue() : "");
        }

        return result;
    }

    private JsonSchema getJsonSchema(com.avaulta.gateway.rules.JsonSchema schema) {

        // TODO: remove this hack; rewritePseudonymToPattern is terrible hack, due to above attempt
        // to register custom format not working


        // TODO: decompress our JsonSchema?
        // - eg, fill `type: object` if there's a `properties` field, etc.
        // - or,`type:string` if there's a `pattern` or `format` field, etc.

        JsonNode schemaNode = objectMapper.valueToTree(rewritePseudonymToPattern(schema));
        return jsonSchemaFactory.getSchema(schemaNode);
    }

    com.avaulta.gateway.rules.JsonSchema rewritePseudonymToPattern(
            com.avaulta.gateway.rules.JsonSchema schema) {
        if (schema.getFormat() != null
                && schema.getFormat().equals(StringFormat.PSEUDONYM.getStringEncoding())) {
            return schema.toBuilder().format(null)
                    .pattern(UrlSafeTokenPseudonymEncoder.REVERSIBLE_PSEUDONYM_PATTERN.pattern())
                    .build();
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

    /**
     * Example of using Jackson's JsonNode API to traverse and transform JSON.
     * This shows how to recursively visit all nodes and conditionally transform them.
     * 
     * @param jsonNode The JSON node to traverse
     * @return Transformed JSON node
     */
    private JsonNode traverseAndTransformJson(JsonNode jsonNode) {
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            ObjectNode transformedNode = objectMapper.createObjectNode();

            // Iterate through all fields in the object
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();

                // Conditionally transform based on field name or value
                if (shouldTransformField(fieldName, fieldValue)) {
                    transformedNode.set(fieldName, transformField(fieldValue));
                } else {
                    // Recursively traverse child nodes
                    transformedNode.set(fieldName, traverseAndTransformJson(fieldValue));
                }
            }
            return transformedNode;
        } else if (jsonNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) jsonNode;
            ArrayNode transformedArray = objectMapper.createArrayNode();

            // Traverse each element in the array
            for (JsonNode element : arrayNode) {
                transformedArray.add(traverseAndTransformJson(element));
            }
            return transformedArray;
        } else {
            // Leaf node (string, number, boolean, null)
            return conditionallyTransformLeafNode(jsonNode);
        }
    }

    /**
     * Example condition to determine if a field should be transformed.
     */
    private boolean shouldTransformField(String fieldName, JsonNode fieldValue) {
        // Example: transform fields with specific names or patterns
        return fieldName.contains("pseudonym") ||
                (fieldValue.isTextual() && fieldValue.asText().startsWith("p~"));
    }

    /**
     * Example transformation for a field.
     */
    private JsonNode transformField(JsonNode fieldValue) {
        // Example: transform pseudonym fields
        if (fieldValue.isTextual()) {
            String value = fieldValue.asText();
            if (value.startsWith("p~")) {
                // Apply some transformation
                return objectMapper.valueToTree("transformed_" + value);
            }
        }
        return fieldValue;
    }

    /**
     * Example transformation for leaf nodes.
     */
    private JsonNode conditionallyTransformLeafNode(JsonNode node) {
        if (node.isTextual()) {
            String value = node.asText();
            // Example: transform specific string patterns
            if (value.matches("^p~[a-zA-Z0-9_-]{43,}$")) {
                return objectMapper.valueToTree("pseudonym_" + value);
            }
        }
        return node;
    }

    /**
     * Example of using Jackson's streaming API to traverse and transform JSON.
     * This is more memory-efficient for large documents.
     * 
     * @param jsonString The JSON string to process
     * @return Transformed JSON string
     */
    @SneakyThrows
    private String traverseAndTransformJsonStreaming(String jsonString) {
        JsonFactory factory = objectMapper.getFactory();
        StringWriter output = new StringWriter();
        JsonGenerator generator = factory.createGenerator(output);

        try (JsonParser parser = factory.createParser(jsonString)) {
            traverseAndTransformStreaming(parser, generator);
        }

        generator.close();
        return output.toString();
    }

    /**
     * Recursively traverse JSON using streaming API.
     */
    @SneakyThrows
    private void traverseAndTransformStreaming(JsonParser parser, JsonGenerator generator) {
        JsonToken token = parser.getCurrentToken();

        switch (token) {
            case START_OBJECT:
                generator.writeStartObject();
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    String fieldName = parser.getCurrentName();
                    parser.nextToken(); // Move to field value

                    // Conditionally transform field name
                    String transformedFieldName =
                            shouldTransformFieldName(fieldName) ? "transformed_" + fieldName
                                    : fieldName;

                    generator.writeFieldName(transformedFieldName);
                    traverseAndTransformStreaming(parser, generator);
                }
                generator.writeEndObject();
                break;

            case START_ARRAY:
                generator.writeStartArray();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    traverseAndTransformStreaming(parser, generator);
                }
                generator.writeEndArray();
                break;

            case VALUE_STRING:
                String value = parser.getValueAsString();
                String transformedValue =
                        shouldTransformValue(value) ? "transformed_" + value : value;
                generator.writeString(transformedValue);
                break;

            case VALUE_NUMBER_INT:
                generator.writeNumber(parser.getLongValue());
                break;

            case VALUE_NUMBER_FLOAT:
                generator.writeNumber(parser.getDoubleValue());
                break;

            case VALUE_TRUE:
                generator.writeBoolean(true);
                break;

            case VALUE_FALSE:
                generator.writeBoolean(false);
                break;

            case VALUE_NULL:
                generator.writeNull();
                break;
        }
    }

    private boolean shouldTransformFieldName(String fieldName) {
        return fieldName.contains("pseudonym");
    }

    private boolean shouldTransformValue(String value) {
        return value != null && value.startsWith("p~");
    }

    /**
     * Example of using Jackson's ObjectMapper with custom deserializer for transformation.
     * This approach is good for complex transformation logic.
     * 
     * @param jsonString The JSON string to transform
     * @return Transformed JSON string
     */
    @SneakyThrows
    private String transformJsonWithCustomDeserializer(String jsonString) {
        // Create a custom ObjectMapper with transformation logic
        ObjectMapper customMapper = new ObjectMapper();

        // Register custom deserializer for specific types
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new TransformingStringDeserializer());
        customMapper.registerModule(module);

        // Parse and re-serialize to apply transformations
        JsonNode node = customMapper.readTree(jsonString);
        return customMapper.writeValueAsString(node);
    }

    /**
     * Custom deserializer that transforms string values.
     */
    private static class TransformingStringDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();

            // Apply transformation logic
            if (value != null && value.startsWith("p~")) {
                return "transformed_" + value;
            }

            return value;
        }
    }


}
