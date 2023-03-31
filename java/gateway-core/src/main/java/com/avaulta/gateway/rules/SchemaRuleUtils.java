package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import lombok.*;

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
    public Object filterObjectBySchema(Object object, JsonSchema schema) {
        JsonNode provisionalOutput = objectMapper.valueToTree(object);
        return filterBySchema(provisionalOutput, schema, schema);
    }

    @SneakyThrows
    public String filterJsonBySchema(String jsonString, JsonSchema schema) {
        JsonNode provisionalOutput = objectMapper.readTree(jsonString);
        return objectMapper.writeValueAsString(filterBySchema(provisionalOutput, schema, schema));
    }


    Object filterBySchema(JsonNode provisionalOutput, JsonSchema schema, JsonSchema root) {
        if (schema.isRef()) {
            if (schema.getRef().equals("#")) {
                // recursive self-reference; see
                return filterBySchema(provisionalOutput, root, root);
            } else if (schema.getRef().startsWith("#/definitions/")) {
                String definitionName = schema.getRef().substring("#/definitions/".length());
                JsonSchema definition = root.getDefinitions().get(definitionName);
                return filterBySchema(provisionalOutput, definition, root);
            } else {
                //cases like URLs relative to schema URI are not supported
                throw new RuntimeException("unsupported ref: " + schema.getRef());
            }
        } else if (schema.hasType()) {
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
                throw new IllegalArgumentException("Unknown schema type: " + schema);
            }
        } else {
            throw new IllegalArgumentException("Only schema with 'type' or '$ref' are supported: " + schema);
        }
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor // for builder
    @Data
    @JsonPropertyOrder({"$schema"})
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties({
        "title",
        "required" // not relevant to 'filter' use case
    })
    public static class JsonSchema {

        //q: should we drop this? only makes sense at root of schema
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

        // part of JSON schema standard, but how to support for filters?
        //  what if something validates against multiple of these, but filtering by the valid ones
        //  yields different result??
        // use case would be polymorphism, such as a groupMembers array can can contain
        // objects of type Group or User, to provide hierarchical groups
        // --> take whichever schema produces the "largest" result (eg, longest as a string??)
        //List<CompoundJsonSchema> anyOf;

        // part of JSON schema standard, but how to support for filters?
        //  what if something validates against multiple of these, but filtering by the valid ones
        //  yields different result??
        // ultimately, don't see a use case anyways
        //List<CompoundJsonSchema> oneOf;

        // part of JSON schema standard
        // it's clear how we would implement this as a filter (chain them), but not why
        // this would ever be a good use case
        //List<CompoundJsonSchema> allOf;

        //part of JSON schema standard, but not a proxy-filtering use case this
        // -- omitting the property produces same result
        // -- no reason you'd ever want to all objects that DON'T match a schema, right?
        // -- only use case I think of is to explicitly note what properties we know are in
        //   source schema, so intend for filter to remove (rather than filter removing them by
        //   omission)
        //CompoundJsonSchema not;


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

        @JsonIgnore
        public boolean hasType() {
            return this.type != null;
        }
    }

}
