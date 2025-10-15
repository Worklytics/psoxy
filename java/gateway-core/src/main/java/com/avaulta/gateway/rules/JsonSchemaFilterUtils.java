package com.avaulta.gateway.rules;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import lombok.extern.java.Log;

@Log
@NoArgsConstructor(access = AccessLevel.PACKAGE) // for tests
@AllArgsConstructor
public class JsonSchemaFilterUtils {

    ObjectMapper objectMapper;


    Options options = Options.builder().build();

    @Builder
    @Value
    public static class Options implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * whether to log each individual redaction made
         */
        @NonNull
        @Builder.Default
        Boolean logRedactions = false;

        /**
         * whether to log a summary of redactions made
         */
        @NonNull
        @Builder.Default
        Boolean logSummarizedRedactions = true;
    }

    /**
     * filter object by properties defined in schema, recursively filtering them by any schema
     * specified for them as well.
     * <p>
     * NOTE: `null` values will be returned for property specified in schema IF value is null in
     * object OR value is of type that doesn't match schema.
     * (eg, `null` is considered to fulfill any type-constraint)
     * <p>
     * TODO support format
     * <p>
     * q: do we want to support filtering by full JsonSchema here?? complex, and not always
     * well-defined.
     *
     * @param object to filter
     * @param schema to filter object's properties by
     * @return object, if matches schema; or sub
     */
    public Pair<Object, List<String>> filterObjectBySchema(Object object, JsonSchemaFilter schema) {
        JsonNode provisionalOutput = objectMapper.valueToTree(object);
        List<String> redactions = new LinkedList<>();
        Object r = filterBySchema("$", provisionalOutput, schema, schema, redactions);
        return Pair.of(r, redactions);
    }

    @SneakyThrows
    public String filterJsonBySchema(String jsonString, JsonSchemaFilter schema,
            JsonSchemaFilter root) {
        JsonNode provisionalOutput = objectMapper.readTree(jsonString);
        return filterJsonBySchema(provisionalOutput, schema, root);
    }

    @SneakyThrows
    public String filterJsonBySchema(JsonNode provisionalOutput, JsonSchemaFilter schema,
            JsonSchemaFilter root) {
        List<String> redactions = new LinkedList<>();
        String r = objectMapper.writeValueAsString(
                filterBySchema("$", provisionalOutput, schema, root, redactions));
        if (options.getLogRedactions()) {
            log.info("Redactions made: " + redactions.stream().collect(Collectors.joining(", ")));
        }
        return r;
    }


    private Object filterBySchema(String path, JsonNode provisionalOutput, JsonSchemaFilter schema,
            JsonSchemaFilter root, List<String> redactionsMade) {
        if (schema.isRef()) {
            if (schema.getRef().equals("#")) {
                // recursive self-reference
                return filterBySchema(path, provisionalOutput, root, root, redactionsMade);
            } else if (schema.getRef().startsWith("#/definitions/")) {
                String definitionName = schema.getRef().substring("#/definitions/".length());
                JsonSchemaFilter definition = root.getDefinitions().get(definitionName);
                if (definition == null) {
                    throw new RuntimeException("definition not found: " + definitionName);
                }
                return filterBySchema(path, provisionalOutput, definition, root, redactionsMade);
            } else {
                // cases like URLs relative to schema URI are not supported
                throw new RuntimeException("unsupported ref: " + schema.getRef());
            }
        } else if (schema.hasOneOf()) {
            // Get first schema with matches its inner condition.
            // See https://json-schema.org/understanding-json-schema/reference/combining.html#oneof
            // NOTE: If is expected that the "oneOf" candidate should hava an if-else-then or
            // if-then nodes
            // inside, otherwise the condition will not be evaluated and only the first occurrence
            // appearing in the list
            // will be chosen
            // DEPRECATED, bc case above is weird to me
            for (JsonSchemaFilter oneOfCandidate : schema.getOneOf()) {
                Object result = filterBySchema(path, provisionalOutput, oneOfCandidate, root,
                        redactionsMade);

                if (result == null && provisionalOutput.isNull()) {
                    return null;
                } else if (result != null ){
                    if (!(result instanceof NotMatchedConstant)) {
                        return result;
                    }
                }
            }

            return null;
        } else if (schema.hasAnyOf()) {
            // Get first schema with matches its inner condition.
            // See https://json-schema.org/understanding-json-schema/reference/combining#anyOf
            // NOTE: If is expected that the "anyOf" candidate should hava an if-else-then or
            // if-then nodes
            // inside, otherwise the condition will not be evaluated and only the first occurrence
            // appearing in the list
            // will be chosen
            for (JsonSchemaFilter anyOfCandidate : schema.getAnyOf()) {
                Object result = filterBySchema(path, provisionalOutput, anyOfCandidate, root, redactionsMade);

                //short-circuit on first match
                if (!(result instanceof NotMatchedConstant)) {
                    return result;
                }
            }

            return null;

        } else if (schema instanceof ConditionJsonSchema) {
            // Conditions are schemas without no type definition
            // Only one property are supported by conditions. See
            // https://json-schema.org/understanding-json-schema/reference/conditionals.html#if-then-else
            // for futher details;
            // in case of more than one property needs to be used to match a condition they should
            // be included inside on allOf/anyOf properties
            Map.Entry<String, JsonSchemaFilter> property =
                    schema.getProperties().entrySet().stream().findFirst()
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Invalid schema, a single property is expected"));

            String key = property.getKey();

            return filterBySchema(path + "." + key, provisionalOutput.get(property.getKey()),
                    schema.getProperties().get(property.getKey()), root, redactionsMade);
        } else if (schema instanceof ThenJsonSchema) {
            // NOTE: Using a LinkedHashMap to keep the fields in the same order
            // they appear defined; otherwise even the final order it seems to be deterministic
            // is might not be the same
            Map<String, Object> filtered = new LinkedHashMap<>();
            provisionalOutput.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                JsonSchemaFilter propertySchema = schema.getProperties().get(key);

                if (propertySchema == null) {
                    if (options.getLogRedactions()) {
                        log.info("Redacted " + path + "." + key + " because it was not in schema");
                    }
                    redactionsMade.add(path + "." + key);
                } else {
                    Object filteredValue = filterBySchema(path + "." + key, value, propertySchema,
                            root, redactionsMade);
                    filtered.put(key, filteredValue);
                }
            });

            return filtered;
        } else if (schema.hasType()) {

            if (schema.hasIf()) {
                Object conditionResult = filterBySchema(path, provisionalOutput,
                        schema.get_if(),
                        root,
                        redactionsMade);

                if (schema.hasElse() && conditionResult instanceof NotMatchedConstant) {
                    conditionResult = filterBySchema(path, provisionalOutput, schema.get_else(),
                            root,
                            redactionsMade);
                }

                if (!(conditionResult instanceof NotMatchedConstant)) {
                    conditionResult = filterBySchema(path, provisionalOutput, schema.get_then(),
                            root,
                            redactionsMade);
                }

                return conditionResult;
            }

            if (schema.hasConstant()) {
                return schema.getConstant().equals(provisionalOutput.asText()) ? ""
                        : NotMatchedConstant.getInstance();
            }

            // must have explicit type
            // https://json-schema.org/understanding-json-schema/reference/type.html
            if (schema.isString()) {
                if (provisionalOutput.isTextual()) {
                    // TODO: validate 'format'??
                    return provisionalOutput.asText();
                } else {
                    if (options.getLogRedactions()) {
                        log.info("Redacted " + path + " because it was not a string");
                    }
                    redactionsMade.add(path);
                    return null;
                }
            } else if (schema.isInteger()) {
                if (provisionalOutput.canConvertToInt() || provisionalOutput.isNull()) {
                    return provisionalOutput.intValue();
                } else if (provisionalOutput.canConvertToLong()) {
                    return provisionalOutput.longValue();
                } else {
                    if (options.getLogRedactions()) {
                        log.info("Redacted " + path + " because it was not an integer");
                    }
                    redactionsMade.add(path);
                    return null;
                }
            } else if (schema.isNumber()) {
                if (provisionalOutput.isNumber() || provisionalOutput.isNull()) {
                    return provisionalOutput.numberValue();
                } else {
                    if (options.getLogRedactions()) {
                        log.info("Redacted " + path + " because it was not a number");
                    }
                    redactionsMade.add(path);
                    return null;
                }
            } else if (schema.isBoolean()) {
                if (provisionalOutput.isBoolean() || provisionalOutput.isNull()) {
                    return provisionalOutput.booleanValue();
                } else {
                    if (options.getLogRedactions()) {
                        log.info("Redacted " + path + " because it was not a boolean");
                    }
                    redactionsMade.add(path);
                    return null;
                }
            } else if (schema.isObject()) {
                if (provisionalOutput.isObject()) {
                    // NOTE: Using a LinkedHashMap to keep the fields in the same order
                    // they appear defined; otherwise even the final order it seems to be
                    // deterministic
                    // is might not be the same
                    Map<String, Object> filtered = new LinkedHashMap<>();

                    Iterator<Map.Entry<String, JsonNode>> iterator = provisionalOutput.fields();
                    while (iterator.hasNext()) {
                        Map.Entry<String, JsonNode> entry = iterator.next();

                        String key = entry.getKey();
                        JsonNode value = entry.getValue();
                        JsonSchemaFilter propertySchema =
                                schema.getProperties() == null ? null
                                        : schema.getProperties().get(key);

                        if (propertySchema == null) {
                            if (options.getLogRedactions()) {
                                log.info("Redacted " + path + "." + key
                                        + " because it was not in schema");
                            }
                            redactionsMade.add(path + "." + key);
                        } else {
                            Object filteredValue = filterBySchema(path + "." + key, value,
                                    propertySchema, root, redactionsMade);

                            if (filteredValue instanceof NotMatchedConstant) {
                                return filteredValue;
                            }
                            filtered.put(key, filteredValue);
                        }
                    }

                    // TODO: add support for `additionalProperties == true`? can't think of a real
                    // life-use case, but in effect it would allow some kind of "sub filtering",
                    // where you can specify a filter just for some known properties, while still
                    // allowing other unexpected properties to be passed through in their entirety

                    // handler for additionalProperties??
                    return filtered;
                } else {
                    if (options.getLogRedactions()) {
                        log.info("Redacted " + path + " because it was not an object");
                    }
                    redactionsMade.add(path);
                    return null;
                }
            } else if (schema.isArray()) {
                if (provisionalOutput.isArray()) {
                    List<Object> filtered = new LinkedList<>();
                    provisionalOutput.elements().forEachRemaining(element -> {
                        Object filteredElement = filterBySchema(path + "[]", element,
                                schema.getItems(), root, redactionsMade);
                        if (filteredElement != null) {
                            filtered.add(filteredElement);
                        }
                    });
                    return filtered;
                } else {
                    if (options.getLogRedactions()) {
                        log.info("Redacted " + path + " because it was not an array");
                    }
                    redactionsMade.add(path);
                    return null;
                }
            } else if (schema.isNull()) {
                // this is kinda nonsensical, right??
                // omit the property --> don't get it
                // include property with {type: null} --> get it, but it's always null?
                // or do we want to FAIL if value from source is NON-NULL?
                if (options.getLogRedactions()) {
                    log.info("Redacted " + path + " because filter expects `null` here");
                }
                redactionsMade.add(path);
                return null;
            } else {
                throw new IllegalArgumentException("Unknown schema type: " + schema);
            }
        } else {
            if (provisionalOutput.isContainerNode()) {
                // log? complex value where only simple leaf type permitted by filter
                if (options.getLogRedactions()) {
                    log.info("Redacted " + path + " because it was not a simple type");
                }
                redactionsMade.add(path);
                return null;
            } else {
                return asSimpleValue(provisionalOutput);
            }
        }
    }

    /**
     * convert JsonNode to simple value (not Object or Array), if possible
     *
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

    /**
     * For building if-else-then conditions in json schema
     * See <a href=
     * "https://json-schema.org/understanding-json-schema/reference/conditionals.html#if-then-else">...</a>
     */
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    public static class ConditionJsonSchema extends JsonSchemaFilter {
    }

    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    public static class ThenJsonSchema extends JsonSchemaFilter {
    }

    private static class NotMatchedConstant {
        private final static NotMatchedConstant instance = new NotMatchedConstant();

        public static NotMatchedConstant getInstance() {
            return instance;
        }
    }

}
