package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PACKAGE) //for tests
@AllArgsConstructor
public class SchemaRuleUtils {

    ObjectMapper objectMapper;
    JsonSchemaGenerator jsonSchemaGenerator;

    /**
     * Generates a JSON schema for the given class.
     * <p>q
     * use case: in client code bases, can generate rules for a given expected result class; perhaps
     * eventually as a build step, eg maven plugin, that writes rules out somewhere
     * <p>
     * eg,  schemaRuleUtils.generateSchema(ExampleResult.class)
     *
     * @param clazz
     * @return
     */
    public JsonSchema generateJsonSchema(Class<?> clazz) {
        JsonNode schema = jsonSchemaGenerator.generateJsonSchema(clazz);
        return objectMapper.convertValue(schema, JsonSchema.class);
    }

    /**
     * filter object by properties defined in schema
     *   TODO support schemas with "$ref"
     *   TODO support format
     *
     * q: do we want to support filtering by full JsonSchema here?? complex, and not always
     * well-defined.
     *
     *
     * @param object to filter
     * @param schema to filter object's properties by
     * @return object, if matches schema; or sub
     */
    public Object filterBySchema(Object object, JsonSchema schema) {
        JsonNode provisionalOutput = objectMapper.valueToTree(object);

        // https://json-schema.org/understanding-json-schema/reference/type.html


        if (schema.isRef()) {
            //TODO: support schema having '$ref' rather than 'type'
            throw new UnsupportedOperationException("schema with '$ref' not supported");
        } else if (schema.isString()) {
            if (provisionalOutput.isTextual()) {
                //TODO: validate 'format'??
                return provisionalOutput.asText();
            } else {
                return null;
            }
        } else if (schema.isNumber()) {
            if (provisionalOutput.isNumber() || provisionalOutput.isNull()) {
                return provisionalOutput.numberValue();
            } else {
                return null;
            }
        } else if (schema.isInteger()) {
            if (provisionalOutput.canConvertToInt() || provisionalOutput.isNull()) {
                return provisionalOutput.intValue();
            } else if (provisionalOutput.canConvertToLong())  {
                return provisionalOutput.longValue();
            } else {
                return null;
            }
        } else if (schema.isBoolean()) {
            if (provisionalOutput.isBoolean() || provisionalOutput.isNull()) {
                return provisionalOutput.booleanValue();
            } else {
                return null;
            }
        } else if (schema.isObject()) {
            if (provisionalOutput.isObject()) {
                Map<String, Object> filtered = new HashMap<>();
                provisionalOutput.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();
                    JsonSchema propertySchema = schema.getProperties().get(key);
                    if (propertySchema != null) {
                        Object filteredValue = filterBySchema(value, propertySchema);
                        filtered.put(key, filteredValue);
                    }
                });

                //TODO: add support for `additionalProperties == true`? not expected use-case for
                // proxy ...

                // handler for additionalProperties??


                return filtered;
            } else {
                return null;
            }
        } else if (schema.isArray()) {
            if (provisionalOutput.isArray()) {
                List<Object> filtered = new LinkedList<>();
                provisionalOutput.elements().forEachRemaining(element -> {
                    Object filteredElement = filterBySchema(element, schema.getItems());
                    if (filteredElement != null) {
                        filtered.add(filteredElement);
                    }
                });
                return filtered;
            } else {
                return null;
            }
        } else {
            throw new IllegalArgumentException("Unknown schema type: " + schema.getType());
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({"$schema", "title"})
    public static class JsonSchema {

        String type;

        @JsonProperty("$ref")
        String ref;

        //only applicable if type==object
        Boolean additionalProperties;


        //only applicable if type==String
        String format;

        Map<String, JsonSchema> properties;

        //only applicable if type==array
        JsonSchema items;

        public boolean isRef() {
            return ref != null;
        }

        public boolean isString() {
            return Objects.equals(type, "string");
        }

        public boolean isNumber() {
            return Objects.equals(type, "number");
        }

        public boolean isInteger() {
            return Objects.equals(type, "integer");
        }
        public boolean isObject() {
            return Objects.equals(type, "object");
        }

        public boolean isArray() {
            return Objects.equals(type, "array");
        }

        public boolean isBoolean() {
            return Objects.equals(type, "boolean");
        }

        public boolean isNull() {
            return Objects.equals(type, "null");
        }
    }
}
