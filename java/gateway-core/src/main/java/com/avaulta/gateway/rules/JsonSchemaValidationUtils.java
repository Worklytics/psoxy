
package com.avaulta.gateway.rules;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.WWWFormCodec;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.rules.JsonSchema.StringFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final static JsonMetaSchema metaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV202012())
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
     * @implNote EXTREMELY alpha; not sure this is a good idea.
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

}
