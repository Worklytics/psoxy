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
        /** Single required string property with enum — constrained label classification. */
        CLASSIFY,
        /** Richer object/array schema — constrained JSON extraction. */
        EXTRACT
    }

    @Value
    public static class ClassifyShape {
        String propertyName;
        List<String> enumValues;
    }

    private GenMetadataSchemaSupport() {
    }

    public static Mode mode(JsonSchemaFilter schema) {
        return classifyShape(schema).isPresent() ? Mode.CLASSIFY : Mode.EXTRACT;
    }

    /**
     * @return classify shape when schema is an object with exactly one property, that property
     * is a string with non-empty {@code enum}, and it is required
     */
    public static Optional<ClassifyShape> classifyShape(JsonSchemaFilter schema) {
        if (schema == null || !schema.isObject()) {
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

    /**
     * If {@code raw} is a bare enum label (optionally quoted), wrap as {@code {property: label}}.
     * Also recovers a label embedded in prose (longest exact enum match), for models that ignore
     * JSON instructions. Returns empty for strings that look like a JSON object (caller should
     * parse those normally first).
     */
    public static Optional<Map<String, Object>> wrapClassifyLabel(String raw, ClassifyShape shape) {
        if (StringUtils.isBlank(raw) || shape == null) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        // Reject obvious JSON objects — those should be parsed normally.
        if (trimmed.startsWith("{")) {
            return Optional.empty();
        }
        for (String allowed : shape.getEnumValues()) {
            if (allowed.equals(trimmed)) {
                return Optional.of(Map.of(shape.getPropertyName(), allowed));
            }
        }
        // Case-insensitive fallback for mild model drift
        for (String allowed : shape.getEnumValues()) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return Optional.of(Map.of(shape.getPropertyName(), allowed));
            }
        }
        return findEnumInText(trimmed, shape);
    }

    /**
     * Longest-first exact (case-insensitive) enum match inside free text / truncated JSON.
     */
    public static Optional<Map<String, Object>> findEnumInText(String text, ClassifyShape shape) {
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
        return Optional.of(Map.of(shape.getPropertyName(), best));
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return StringUtils.containsIgnoreCase(haystack, needle);
    }
}
