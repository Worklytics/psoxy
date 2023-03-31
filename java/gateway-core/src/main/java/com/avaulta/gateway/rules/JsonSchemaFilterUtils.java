package com.avaulta.gateway.rules;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kjetland.jackson.jsonSchema.JsonSchemaGenerator;
import lombok.*;

import java.util.*;

@NoArgsConstructor(access = AccessLevel.PACKAGE) //for tests
@AllArgsConstructor
public class JsonSchemaFilterUtils {

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
    public JsonSchemaFilter generateJsonSchemaFilter(Class<?> clazz) {
        JsonNode schema = jsonSchemaGenerator.generateJsonSchema(clazz);
        return objectMapper.convertValue(schema, JsonSchemaFilter.class);
    }

    /**
     *
     * TODO: really doesn't need to be in proxy codebase; it's just for rule generation
     *
     * @param filter to compact
     * @return a compact copy of the schema filter
     */
    @SneakyThrows
    public JsonSchemaFilter compactCopy(JsonSchemaFilter filter) {
        //quick, lame approach to have a deep copy of schema
        JsonSchemaFilter copy = objectMapper.readerFor(JsonSchemaFilter.class)
            .readValue(objectMapper.writeValueAsString(filter));

        compact(copy);

        return copy;
    }

    /**
     * mutating compaction - removes all simple-type information from filter
     * @param filter
     */
    void compact(JsonSchemaFilter filter) {
        if (filter.isArray()) {
            compact(filter.getItems());
        } else if (filter.isObject()) {
            filter.getProperties().forEach((k, v) -> compact(v));
        } else if (filter.hasType()) { // as non-complex type
            filter.setType(null);
        }

        // also compact any definitions
        if (filter.getDefinitions() != null) {
            filter.getDefinitions().forEach((k, v) -> compact(v));
        }
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
    public Object filterObjectBySchema(Object object, JsonSchemaFilter schema) {
        JsonNode provisionalOutput = objectMapper.valueToTree(object);
        return filterBySchema(provisionalOutput, schema, schema);
    }

    @SneakyThrows
    public String filterJsonBySchema(String jsonString, JsonSchemaFilter schema, JsonSchemaFilter root) {
        JsonNode provisionalOutput = objectMapper.readTree(jsonString);
        return objectMapper.writeValueAsString(filterBySchema(provisionalOutput, schema, root));
    }


    Object filterBySchema(JsonNode provisionalOutput, JsonSchemaFilter schema, JsonSchemaFilter root) {
        if (schema.isRef()) {
            if (schema.getRef().equals("#")) {
                // recursive self-reference; see
                return filterBySchema(provisionalOutput, root, root);
            } else if (schema.getRef().startsWith("#/definitions/")) {
                String definitionName = schema.getRef().substring("#/definitions/".length());
                JsonSchemaFilter definition = root.getDefinitions().get(definitionName);
                if (definition == null) {
                    throw new RuntimeException("definition not found: " + definitionName);
                }
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
            } else if (schema.isInteger()) {
                if (provisionalOutput.canConvertToInt() || provisionalOutput.isNull()) {
                    return provisionalOutput.intValue();
                } else if (provisionalOutput.canConvertToLong()) {
                    return provisionalOutput.longValue();
                } else {
                    return null;
                }
            } else if (schema.isNumber()) {
                if (provisionalOutput.isNumber() || provisionalOutput.isNull()) {
                    return provisionalOutput.numberValue();
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
                        JsonSchemaFilter propertySchema = schema.getProperties().get(key);
                        if (propertySchema != null) {
                            Object filteredValue = filterBySchema(value, propertySchema, root);
                            filtered.put(key, filteredValue);
                        }
                    });

                    //TODO: add support for `additionalProperties == true`? can't think of a real
                    // life-use case, but in effect it would allow some kind of "sub filtering",
                    // where you can specify a filter just for some known properties, while still
                    // allowing other unexpected properties to be passed through in their entirety

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
            if (provisionalOutput.isContainerNode()) {
                // log? complex value where only simple leaf type permitted by filter
                return null;
            } else {
                return asSimpleValue(provisionalOutput);
            }
        }
    }

    /**
     * convert JsonNode to simple value (not Object or Array), if possible
     * @param node to convert
     * @return Java equivalent of node's value, if its a simple type (not Object or Array)
     */
    Object asSimpleValue(JsonNode node) {
        if (node.isTextual()) {
            return node.asText();
        } else if (node.isInt()) {
            return node.intValue();
        } else if (node.isLong()) {
            return node.longValue();
        } else if (node.isDouble()) {
            return node.doubleValue();
        } else if (node.isBoolean()) {
            return node.booleanValue();
        } else if (node.isNull()) {
            return null;
        } else {
            throw new IllegalArgumentException("Not a simple type: " + node);
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
        "required", // not relevant to 'filter' use case
        "additionalProperties", // not currently supported
        "$schema", // not helpful in filter use-case, although maybe should include in future
    })
    public static class JsonSchemaFilter {

        //dropped for now
        //@JsonProperty("$schema")
        //String schema;

        String type;

        @JsonProperty("$ref")
        String ref;

        //only applicable if type==String
        String format;

        Map<String, JsonSchemaFilter> properties;

        //only applicable if type==array
        JsonSchemaFilter items;

        // @JsonProperties("$defs") on property, lombok getter/setter don't seem to do the right thing
        // get java.lang.IllegalArgumentException: Unrecognized field "definitions" (class com.avaulta.gateway.rules.SchemaRuleUtils$JsonSchema)
        //        //  when calling objectMapper.convertValue(((JsonNode) schema), JsonSchema.class);??
        // perhaps it's a problem with the library we use to build the JsonNode schema??

        //TODO: this is not used except from the 'root' schema; is that correct??
        Map<String, JsonSchemaFilter> definitions;

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

        @JsonIgnore
        public boolean isComplex() {
            return isObject() || isArray();
        }
    }

}
