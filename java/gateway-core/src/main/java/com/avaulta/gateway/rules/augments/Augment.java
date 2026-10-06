package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchema;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for augments — computed field enrichment rules that add synthetic
 * sibling properties to API response payloads.
 *
 * <p>Augments run <b>before</b> transforms, so transforms still see original field values.
 * The output is placed in a sibling property named {@code +{sourceProperty}:{augmentFunction}},
 * or on the matched object itself as {@code +self:{augmentFunction}} when the jsonPath matches
 * a JSON object (Map).
 *
 * @see <a href="file:///docs/development/augments.md">Augments Design Doc</a>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Augment.Classify.class, name = "classify"),
    @JsonSubTypes.Type(value = Augment.TextDigest.class, name = "textDigest"),
    @JsonSubTypes.Type(value = Augment.TextMetadata.class, name = "textMetadata"),
    @JsonSubTypes.Type(value = Augment.GenMetadata.class, name = "genMetadata"),
    @JsonSubTypes.Type(value = Augment.RegexExtract.class, name = "regexExtract"),
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
    JsonSchema outputSchema;

    /**
     * If provided, the source value is treated as a JSON string and parsed, and this JSONPath
     * is evaluated against it to extract the actual value(s) to augment.
     *
     * <p>Note: This is the augment equivalent of the {@code jsonPathToProcessWhenEscaped} option
     * that exists for some transforms. Its presence implicitly enables escaped JSON decoding.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @Builder.Default
    String innerJsonPath = null;

    /**
     * Returns the augment function name, used in the synthetic property name
     * {@code +{sourceProperty}:{augmentFunction}}.
     */
    @JsonIgnore
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

        @JsonIgnore
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
                        if (StringUtils.isNotBlank(k)) {
                           keys.add(k.toLowerCase());
                        }
                    }
                }
                keys = Set.copyOf(keys); // immutable snapshot
                searchKeys = keys;
            }
            return keys;
        }
    }

    /**
     * Performs NLP analysis on text fields, extracting sentence structure, POS, and derived signals.
     */
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class TextMetadata extends Augment {

        static final List<String> DEFAULT_HEDGE_WORDS =
            List.of("maybe", "perhaps", "kind", "sort", "probably", "somewhat", "possibly");
        static final List<String> DEFAULT_CONSTRAINT_WORDS =
            List.of("must", "only", "never", "always", "don't", "avoid", "require", "cannot");

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Builder.Default
        Map<String, List<String>> taxonomy = new TreeMap<>();

        /**
         * Additional hedge signal words (merged with {@link #DEFAULT_HEDGE_WORDS}).
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Builder.Default
        List<String> hedgeWords = new ArrayList<>();

        /**
         * Additional constraint signal words (merged with {@link #DEFAULT_CONSTRAINT_WORDS}).
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Builder.Default
        List<String> constraintWords = new ArrayList<>();

        @Override
        public String getFunctionName() {
            return "textMetadata";
        }

        @Override
        public Object compute(Object input) {
            // Computed at runtime by AugmentProcessor via injected TextMetadataProcessor.
            return null;
        }

        static Set<String> signalWords(List<String> configured, List<String> defaults) {
            Set<String> result = new HashSet<>();
            defaults.forEach(word -> result.add(word.toLowerCase()));
            if (configured != null) {
                configured.stream()
                    .filter(StringUtils::isNotBlank)
                    .forEach(word -> result.add(word.toLowerCase()));
            }
            return Set.copyOf(result);
        }
    }

    /**
     * BETA: Closed-set classification via cloud LLM (Bedrock / Vertex). Output is exactly one of
     * {@link #classes}. {@code maxOutputTokens} is inferred as the longest class string.
     */
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode(callSuper = true)
    @JsonIgnoreProperties({"outputSchema", "maxOutputTokens", "maxTokens"})
    public static class Classify extends Augment {

        public static final int DEFAULT_MAX_INPUT_TOKENS = 256;

        /**
         * Extra generation tokens so the model can wrap the class name ({@code class:foo}) and we
         * can still match it as a token.
         */
        public static final int MAX_OUTPUT_TOKEN_SLACK = 16;

        /**
         * Task instruction passed to the generative backend (how to choose among {@link #classes}).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String prompt;

        /**
         * Allowed class names (JSON Schema enum equivalent). The model output must be exactly one
         * of these strings.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Singular
        List<String> classes;

        /**
         * Cap on the <em>dynamic</em> source corpus (serialized jsonPath match).
         * The static task {@link #prompt} and class list are not counted.
         * Default {@value #DEFAULT_MAX_INPUT_TOKENS}.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Getter(AccessLevel.NONE)
        Integer maxInputTokens;

        /**
         * Generation cap inferred from {@link #classes}: length of the longest class name.
         */
        @JsonIgnore
        public int getMaxOutputTokens() {
            int max = 0;
            if (classes != null) {
                for (String value : classes) {
                    if (value != null && value.length() > max) {
                        max = value.length();
                    }
                }
            }
            if (max < 1) {
                throw new IllegalArgumentException(
                    "classify classes must include a non-empty string");
            }
            return max;
        }

        public int getMaxInputTokens() {
            if (maxInputTokens == null) {
                return DEFAULT_MAX_INPUT_TOKENS;
            }
            if (maxInputTokens < 1) {
                throw new IllegalArgumentException(
                    "maxInputTokens must be >= 1, got " + maxInputTokens);
            }
            return maxInputTokens;
        }

        @JsonIgnore
        @Override
        public String getFunctionName() {
            return "classify";
        }

        @Override
        public Object compute(Object input) {
            return null;
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof Classify;
        }
    }

    /**
     * BETA: Generates structured metadata via cloud LLM (Bedrock / Vertex) from a JSON Schema.
     * Requires {@link #outputSchema} and {@link #prompt}; model/backend selection is deployment config.
     * For closed-set labels use {@link Classify}.
     */
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class GenMetadata extends Augment {

        public static final int DEFAULT_MAX_INPUT_TOKENS = 256;

        /**
         * Default generation cap. Enough for small JSON objects; transcripts / rich extract
         * should set {@link #maxOutputTokens} higher ({@code 500}+).
         */
        public static final int DEFAULT_MAX_OUTPUT_TOKENS = 200;

        /**
         * Task instruction passed to the generative backend.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String prompt;

        /**
         * Per-augment cap on generated tokens (visible JSON; Gemini thinking shares this
         * budget). Default {@value #DEFAULT_MAX_OUTPUT_TOKENS}. Meeting transcripts / rich
         * extract should set this higher ({@code 500}+).
         */
        @JsonAlias("maxTokens")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Getter(AccessLevel.NONE)
        Integer maxOutputTokens;

        /**
         * Cap on the <em>dynamic</em> source corpus (serialized jsonPath match).
         * The static task {@link #prompt} and schema/labels are not counted.
         * Default {@value #DEFAULT_MAX_INPUT_TOKENS}. Longer extract: 500+.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Getter(AccessLevel.NONE)
        Integer maxInputTokens;

        /**
         * Output token cap for this augment. Returns configured value or
         * {@value #DEFAULT_MAX_OUTPUT_TOKENS} when unset.
         * @throws IllegalArgumentException if configured value is less than 1
         */
        public int getMaxOutputTokens() {
            if (maxOutputTokens == null) {
                return DEFAULT_MAX_OUTPUT_TOKENS;
            }
            if (maxOutputTokens < 1) {
                throw new IllegalArgumentException("maxOutputTokens must be >= 1, got " + maxOutputTokens);
            }
            return maxOutputTokens;
        }

        /**
         * Input token cap for the dynamic source corpus. Returns configured value or
         * {@value #DEFAULT_MAX_INPUT_TOKENS} when unset.
         * @throws IllegalArgumentException if configured value is less than 1
         */
        public int getMaxInputTokens() {
            if (maxInputTokens == null) {
                return DEFAULT_MAX_INPUT_TOKENS;
            }
            if (maxInputTokens < 1) {
                throw new IllegalArgumentException("maxInputTokens must be >= 1, got " + maxInputTokens);
            }
            return maxInputTokens;
        }

        @JsonIgnore
        @Override
        public String getFunctionName() {
            return "genMetadata";
        }

        @Override
        public Object compute(Object input) {
            // Computed at runtime by AugmentProcessor via injected GenMetadataProcessor.
            return null;
        }

        @Override
        protected boolean canEqual(Object other) {
            return other instanceof GenMetadata;
        }
    }

    /**
     * Extracts structured values from text using ordered regex rules per output field.
     * The first matching rule for each field wins; fields with no match are omitted.
     */
    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class RegexExtract extends Augment {

        /**
         * Output field name to ordered extraction rules. First match per field is used.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Singular
        Map<String, List<ExtractionRule>> extractions;

        /**
         * When the matched value is a JSON object, concatenate these field values (in order,
         * separated by newlines) before applying {@link #extractions}. Ignored for string inputs.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @Singular("sourceField")
        List<String> sourceFields;

        @JsonIgnore
        @Getter(AccessLevel.NONE)
        @EqualsAndHashCode.Exclude
        private final ConcurrentHashMap<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

        @JsonIgnore
        @Override
        public String getFunctionName() {
            return "regexExtract";
        }

        @Override
        public Object compute(Object input) {
            String text = toSearchText(input);
            if (text == null || text.isEmpty()) {
                return null;
            }
            if (extractions == null || extractions.isEmpty()) {
                return null;
            }
            TreeMap<String, Object> result = new TreeMap<>();
            for (Map.Entry<String, List<ExtractionRule>> entry : extractions.entrySet()) {
                String field = entry.getKey();
                if (StringUtils.isBlank(field) || entry.getValue() == null) {
                    continue;
                }
                for (ExtractionRule rule : entry.getValue()) {
                    String extracted = rule.extract(text, compiledPatterns);
                    if (extracted != null && !extracted.isEmpty()) {
                        result.put(field, extracted);
                        break;
                    }
                }
            }
            return result.isEmpty() ? null : result;
        }

        @SuppressWarnings("unchecked")
        private String toSearchText(Object input) {
            if (input instanceof String s) {
                return s;
            }
            if (input instanceof Map<?, ?> map && sourceFields != null && !sourceFields.isEmpty()) {
                StringBuilder combined = new StringBuilder();
                for (String field : sourceFields) {
                    if (StringUtils.isBlank(field)) {
                        continue;
                    }
                    Object value = map.get(field);
                    if (value instanceof String s && !s.isEmpty()) {
                        if (combined.length() > 0) {
                            combined.append('\n');
                        }
                        combined.append(s);
                    }
                }
                return combined.length() == 0 ? null : combined.toString();
            }
            return null;
        }

        @SuperBuilder(toBuilder = true)
        @NoArgsConstructor
        @AllArgsConstructor
        @Getter
        @EqualsAndHashCode
        public static class ExtractionRule {

            /**
             * Java regex pattern. When {@link #value} is set, a match assigns that literal;
             * otherwise the configured {@link #group} (default 1) is extracted.
             */
            String regex;

            /**
             * Literal value to assign when {@link #regex} matches. Mutually exclusive with
             * extracting a capture group unless both are set (literal wins).
             */
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String value;

            /**
             * 1-based capture group to extract when {@link #value} is not set. Defaults to 1.
             */
            @JsonInclude(JsonInclude.Include.NON_NULL)
            Integer group;

            String extract(String text, ConcurrentHashMap<String, Pattern> patternCache) {
                if (StringUtils.isBlank(regex) || text == null) {
                    return null;
                }
                Pattern pattern = patternCache.computeIfAbsent(regex, Pattern::compile);
                Matcher matcher = pattern.matcher(text);
                if (!matcher.find()) {
                    return null;
                }
                if (StringUtils.isNotBlank(value)) {
                    return value;
                }
                int captureGroup = group != null ? group : 1;
                if (captureGroup < 1 || captureGroup > matcher.groupCount()) {
                    return null;
                }
                String captured = matcher.group(captureGroup);
                return captured == null ? null : captured.trim();
            }
        }
    }
}
