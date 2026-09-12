package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Infers genMetadata inference mode from {@link JsonSchemaFilter} (classify vs extract).
 */
public final class GenMetadataSchemaSupport {

    public enum Mode {
        /** Closed-vocab label: root string enum, or object with one required string enum property. */
        CLASSIFY,
        /** Richer object/array schema — constrained JSON extraction. */
        EXTRACT
    }

    @Value
    public static class ClassifyShape {
        /** Property name for object classify; {@code null} when schema is a root string enum. */
        String propertyName;
        List<String> enumValues;

        public boolean isRootString() {
            return StringUtils.isBlank(propertyName);
        }
    }

    private GenMetadataSchemaSupport() {
    }

    public static Mode mode(JsonSchemaFilter schema) {
        return classifyShape(schema).isPresent() ? Mode.CLASSIFY : Mode.EXTRACT;
    }

    /**
     * @return classify shape when schema is a root string {@code enum}, or an object with exactly
     * one required string property that has a non-empty {@code enum}
     */
    public static Optional<ClassifyShape> classifyShape(JsonSchemaFilter schema) {
        if (schema == null) {
            return Optional.empty();
        }
        Optional<ClassifyShape> rootString = rootStringEnum(schema);
        if (rootString.isPresent()) {
            return rootString;
        }
        if (!schema.isObject()) {
            return Optional.empty();
        }
        Map<String, JsonSchemaFilter> properties = schema.getProperties();
        if (properties == null || properties.size() != 1) {
            return Optional.empty();
        }
        Map.Entry<String, JsonSchemaFilter> only = properties.entrySet().iterator().next();
        String name = only.getKey();
        JsonSchemaFilter prop = only.getValue();
        if (prop == null || !prop.isString()) {
            return Optional.empty();
        }
        List<String> enums = prop.getEnumValues();
        if (enums == null || enums.isEmpty()) {
            return Optional.empty();
        }
        List<String> required = schema.getRequired();
        if (required == null || !required.contains(name)) {
            return Optional.empty();
        }
        return Optional.of(new ClassifyShape(name, List.copyOf(enums)));
    }

    static Optional<ClassifyShape> rootStringEnum(JsonSchemaFilter schema) {
        if (!schema.isString()) {
            return Optional.empty();
        }
        List<String> enums = schema.getEnumValues();
        if (enums == null || enums.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new ClassifyShape(null, List.copyOf(enums)));
    }

    /**
     * Recover a closed-vocab label from a model response (bare, quoted JSON string, or prose).
     */
    public static Optional<String> recoverLabel(String raw, ClassifyShape shape) {
        if (StringUtils.isBlank(raw) || shape == null) {
            return Optional.empty();
        }
        String trimmed = unwrapQuoted(raw.trim());
        // Reject obvious JSON objects for object-shaped classify — those should be parsed as JSON.
        if (!shape.isRootString() && trimmed.startsWith("{")) {
            return Optional.empty();
        }
        for (String allowed : shape.getEnumValues()) {
            if (allowed.equals(trimmed)) {
                return Optional.of(allowed);
            }
        }
        for (String allowed : shape.getEnumValues()) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return Optional.of(allowed);
            }
        }
        return findEnumLabelInText(trimmed, shape);
    }

    /**
     * If {@code raw} is a bare enum label (optionally quoted), wrap as {@code {property: label}}.
     * Also recovers a label embedded in prose (longest exact enum match), for models that ignore
     * JSON instructions. Returns empty for strings that look like a JSON object (caller should
     * parse those normally first), and for root-string classify schemas.
     */
    public static Optional<Map<String, Object>> wrapClassifyLabel(String raw, ClassifyShape shape) {
        if (shape == null || shape.isRootString()) {
            return Optional.empty();
        }
        return recoverLabel(raw, shape).map(label -> Map.of(shape.getPropertyName(), (Object) label));
    }

    /**
     * Longest-first exact (case-insensitive) enum match inside free text / truncated JSON.
     */
    public static Optional<Map<String, Object>> findEnumInText(String text, ClassifyShape shape) {
        if (shape == null || shape.isRootString()) {
            return Optional.empty();
        }
        return findEnumLabelInText(text, shape)
            .map(label -> Map.of(shape.getPropertyName(), (Object) label));
    }

    public static Optional<String> findEnumLabelInText(String text, ClassifyShape shape) {
        if (StringUtils.isBlank(text) || shape == null) {
            return Optional.empty();
        }
        String haystack = text.trim();
        String best = null;
        for (String allowed : shape.getEnumValues()) {
            if (StringUtils.isBlank(allowed)) {
                continue;
            }
            if (containsIgnoreCase(haystack, allowed)
                && (best == null || allowed.length() > best.length())) {
                best = allowed;
            }
        }
        if (best == null) {
            return Optional.empty();
        }
        return Optional.of(best);
    }

    /**
     * If a model ignored string-enum instructions and returned {@code {"category":"Label"}},
     * take the first value that matches the closed vocab.
     */
    public static Optional<String> labelFromMap(Map<?, ?> map, ClassifyShape shape) {
        if (map == null || shape == null) {
            return Optional.empty();
        }
        for (Object value : map.values()) {
            if (value == null) {
                continue;
            }
            Optional<String> label = recoverLabel(String.valueOf(value), shape);
            if (label.isPresent()) {
                return label;
            }
        }
        return Optional.empty();
    }

    static String unwrapQuoted(String trimmed) {
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return StringUtils.containsIgnoreCase(haystack, needle);
    }
}
