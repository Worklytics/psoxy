package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Base class for augments — computed field enrichment rules that add synthetic
 * sibling properties to API response payloads.
 *
 * <p>Augments run <b>before</b> transforms, so transforms still see original field values.
 * The output is placed in a sibling property named {@code +{sourceProperty}:{augmentFunction}}.
 *
 * @see <a href="file:///docs/development/augments.md">Augments Design Doc</a>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Augment.TextDigest.class, name = "textDigest"),
})
@SuperBuilder(toBuilder = true)
@AllArgsConstructor
@NoArgsConstructor
@Getter
@EqualsAndHashCode
public abstract class Augment {

    //NOTE: this is filled for JSON, but for YAML a YAML-specific type syntax is used:
    // !<textDigest>
    // Jackson YAML can still *read* yaml-encoded augment with `method: "textDigest"`
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String method;

    /**
     * Specifies JSON paths within content that identify values to compute the augment from.
     *
     * <p>Each value matching each path will be passed to the augment's {@link #compute} method
     * as a separate invocation.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Singular
    List<String> jsonPaths;

    /**
     * Schema applied as a predicate to the augment's output value.
     * If the output does not conform, the augment value is dropped
     * (warning logged) but the response is otherwise unaffected.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonSchemaFilter outputSchema;

    /**
     * Returns the augment function name, used in the synthetic property name
     * {@code +{sourceProperty}:{augmentFunction}}.
     */
    public abstract String getFunctionName();

    /**
     * Compute the augment value for a single input.
     *
     * @param input the source field value (typically a String)
     * @return the computed augment output (typically a Map/TreeMap), or null if not computable
     */
    public abstract Object compute(Object input);

    // --- Concrete subclasses ---

    /**
     * Computes text statistics from a string field: length, word count, and optional keyword frequency.
     *
     * <p>This augment replaces the deprecated {@code Transform.TextDigest}, producing the same
     * output but as a sibling property rather than replacing the source field's value.
     */
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class TextDigest extends Augment {

        /**
         * BETA - behavior subject to change or removal.
         * If provided, counts occurrences of these keywords in the text (case-insensitive).
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Builder.Default
        List<String> keywords = new ArrayList<>();

        @Override
        public String getFunctionName() {
            return "textDigest";
        }

        @Override
        public Object compute(Object input) {
            if (!(input instanceof String text)) {
                return null;
            }
            return generate(text);
        }

        /**
         * Generate text digest statistics from a string.
         * Reuses the same logic as {@code Transform.TextDigest.generate()}.
         */
        public TreeMap<String, Object> generate(String text) {
            TreeMap<String, Object> result = new TreeMap<>();

            if (text == null || text.isEmpty()) {
                result.put("length", 0);
                result.put("word_count", 0);
                return result;
            }

            result.put("length", text.length());
            result.put("word_count", text.trim().split("\\s+").length);

            Set<String> keys = getSearchKeys();
            if (!keys.isEmpty()) {
                String[] wordTokens = text.toLowerCase().split("\\W+");
                Map<String, Integer> matchedKeywords = new TreeMap<>();
                for (String t : wordTokens) {
                    if (keys.contains(t)) {
                        matchedKeywords.put(t, matchedKeywords.getOrDefault(t, 0) + 1);
                    }
                }
                if (!matchedKeywords.isEmpty()) {
                    result.put("keywords", matchedKeywords);
                }
            }
            return result;
        }

        /**
         * Lazily computed, thread-safe cache of lowercased keyword set.
         * Volatile ensures visibility across threads; the set itself is
         * effectively immutable once computed.
         */
        private volatile Set<String> searchKeys;

        private Set<String> getSearchKeys() {
            Set<String> keys = searchKeys;
            if (keys == null) {
                keys = new HashSet<>();
                if (keywords != null) {
                    for (String k : keywords) {
                        keys.add(k.toLowerCase());
                    }
                }
                keys = Set.copyOf(keys); // immutable snapshot
                searchKeys = keys;
            }
            return keys;
        }
    }
}
