package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.*;
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
     * filter object by properties defined in schema, recursively filtering them by any schema
     * specified for them as well.
     *
     * NOTE: `null` values will be returned for property specified in schema IF value is null in
     * object OR value is of type that doesn't match schema.
     *  (eg, `null` is considered to fulfill any type-constraint)
     *
     *
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
        return filterBySchema(object, schema, schema);

    }


    Object filterBySchema(Object object, JsonSchema schema, JsonSchema root) {
        JsonNode provisionalOutput = objectMapper.valueToTree(object);

        if (schema.isRef()) {
            if (schema.getRef().equals("#")) {
                // recursive self-reference; see
                return filterBySchema(object, root, root);
            } else if (schema.getRef().startsWith("#/definitions/")) {
                String definitionName = schema.getRef().substring("#/definitions/".length());
                JsonSchema definition = root.getDefinitions().get(definitionName);
                return filterBySchema(object, definition, root);
            } else {
                //cases like URLs relative to schema URI are not supported
                throw new RuntimeException("unsupported ref: " + schema.getRef());
            }
        } else {
            //must have explicit type

            // https://json-schema.org/understanding-json-schema/reference/type.html
            if (schema.isString()) {
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
                } else if (provisionalOutput.canConvertToLong()) {
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
                            Object filteredValue = filterBySchema(value, propertySchema, root);
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
                        Object filteredElement = filterBySchema(element, schema.getItems(), root);
                        if (filteredElement != null) {
                            filtered.add(filteredElement);
                        }
                    });
                    return filtered;
                } else {
                    return null;
                }
            } else if (schema.isNull()) {
                //this is kinda nonsensical, right??
                // omit the property --> don't get it
                // include property with {type: null} --> get it, but it's always null?
                // or do we want to FAIL if value from source is NON-NULL?
                return null;
            } else {
                throw new IllegalArgumentException("Unknown schema type: " + schema.getType());
            }
        }
    }

    @Data
    @JsonPropertyOrder({"$schema"})
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({"title"})
    public static class JsonSchema {

        @JsonProperty("$schema")
        String schema;

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

        // @JsonProperties("$defs") on property, lombok getter/setter don't seem to do the right thing
        // get java.lang.IllegalArgumentException: Unrecognized field "definitions" (class com.avaulta.gateway.rules.SchemaRuleUtils$JsonSchema)
        //        //  when calling objectMapper.convertValue(((JsonNode) schema), JsonSchema.class);??
        // perhaps it's a problem with the library we use to build the JsonNode schema??
        Map<String, JsonSchema> definitions;

        @JsonIgnore
        public boolean isRef() {
            return ref != null;
        }

        @JsonIgnore
        public boolean isString() {
            return Objects.equals(type, "string");
        }

        @JsonIgnore
        public boolean isNumber() {
            return Objects.equals(type, "number");
        }

        @JsonIgnore
        public boolean isInteger() {
            return Objects.equals(type, "integer");
        }

        @JsonIgnore
        public boolean isObject() {
            return Objects.equals(type, "object");
        }

        @JsonIgnore
        public boolean isArray() {
            return Objects.equals(type, "array");
        }

        @JsonIgnore
        public boolean isBoolean() {
            return Objects.equals(type, "boolean");
        }

        @JsonIgnore
        public boolean isNull() {
            return Objects.equals(type, "null");
        }
    }


    /**
     * compound JsonSchema - eg, composition of multiple schemas
     *
     * properties anyOf/allOf/oneOf/not allow you to compose types for a single property / support
     * polymorphism; but not sure we currently have a use-case for these in Proxy.
     *
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({"title"})
    public static class CompoundJsonSchema extends JsonSchema {
        List<CompoundJsonSchema> anyOf;

        List<CompoundJsonSchema> allOf;

        List<CompoundJsonSchema> oneOf;

        CompoundJsonSchema not;
    }
}
