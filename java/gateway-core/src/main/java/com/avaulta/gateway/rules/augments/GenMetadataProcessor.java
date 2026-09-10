package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facade for {@link Augment.GenMetadata} — delegates to a configured {@link GenMetadataBackend}.
 */
public class GenMetadataProcessor {

    private static final Logger log = Logger.getLogger(GenMetadataProcessor.class.getName());

    private static final int DEFAULT_MAX_INPUT_CHARS = 4096;
    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final int MAX_LOG_OUTPUT_CHARS = 2000;

    private final GenMetadataBackend backend;
    private final ObjectMapper objectMapper;
    private final JsonSchemaValidationUtils jsonSchemaValidationUtils;
    private final int maxInputChars;
    private final int maxAttempts;

    public GenMetadataProcessor(GenMetadataBackend backend, ObjectMapper objectMapper, int maxInputChars,
                                int maxAttempts, JsonSchemaValidationUtils jsonSchemaValidationUtils) {
        this.backend = backend != null ? backend : new UnavailableGenMetadataBackend();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.jsonSchemaValidationUtils = jsonSchemaValidationUtils != null
            ? jsonSchemaValidationUtils
            : new JsonSchemaValidationUtils();
        this.maxInputChars = maxInputChars > 0 ? maxInputChars : DEFAULT_MAX_INPUT_CHARS;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
    }

    public GenMetadataProcessor(GenMetadataBackend backend, ObjectMapper objectMapper, int maxInputChars) {
        this(backend, objectMapper, maxInputChars, DEFAULT_MAX_ATTEMPTS, null);
    }

    public GenMetadataProcessor(GenMetadataBackend backend, ObjectMapper objectMapper) {
        this(backend, objectMapper, DEFAULT_MAX_INPUT_CHARS);
    }

    /**
     * Compute genMetadata output for a single augment invocation.
     */
    public Object compute(Augment.GenMetadata augment, Object input) {
        return process(augment.getPrompt(), augment.getOutputSchema(), input);
    }

    public Object process(String taskPrompt, JsonSchemaFilter outputSchema, Object input) {
        if (StringUtils.isBlank(taskPrompt) || outputSchema == null) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "genMetadata missing prompt or outputSchema");
        }
        String inputJson = serializeInput(input);
        if (inputJson == null) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "genMetadata input empty or not serializable");
        }
        try {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (attempt > 1) {
                    log.info("genMetadata inference retry attempt " + attempt + " of " + maxAttempts);
                }
                Object parsed = inferOnce(taskPrompt, outputSchema, inputJson);
                if (parsed != null && validatesOutputSchema(parsed, outputSchema)) {
                    return parsed;
                }
            }
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.INFERENCE_FAILED,
                "genMetadata output failed schema validation after " + maxAttempts + " attempt(s)");
        } catch (GenMetadataAugmentException e) {
            throw e;
        } catch (Exception e) {
            log.log(Level.WARNING, "genMetadata inference failed", e);
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.INFERENCE_FAILED,
                "genMetadata inference failed", e);
        }
    }

    private Object inferOnce(String taskPrompt, JsonSchemaFilter outputSchema, String inputJson)
            throws Exception {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        log.info("genMetadata augment inference call started at " + startedAt);
        Object raw;
        try {
            raw = backend.generate(taskPrompt, outputSchema, inputJson);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            log.info("genMetadata augment inference call completed in " + elapsedMs + "ms");
        }
        if (raw instanceof String rawText) {
            log.info("genMetadata raw backend response: " + truncateForLog(rawText));
        } else if (raw != null) {
            log.info("genMetadata backend returned non-string type: "
                + raw.getClass().getSimpleName());
        }
        Object parsed = parseModelJson(raw, outputSchema);
        if (parsed == null) {
            log.warning("genMetadata backend returned unparseable output");
            return null;
        }
        if (parsed instanceof Map<?, ?> map) {
            log.info("genMetadata parsed output keys: " + map.keySet()
                + "; value=" + truncateForLog(serializeForLog(parsed)));
        } else {
            log.info("genMetadata parsed output: " + truncateForLog(serializeForLog(parsed)));
        }
        return parsed;
    }

    private boolean validatesOutputSchema(Object parsed, JsonSchemaFilter outputSchema) {
        try {
            String json = objectMapper.writeValueAsString(parsed);
            return jsonSchemaValidationUtils.validateJsonBySchema(json, outputSchema);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to validate genMetadata output schema", e);
            return false;
        }
    }

    String serializeInput(Object input) {
        if (input == null) {
            return null;
        }
        try {
            if (input instanceof String text) {
                if (text.isEmpty()) {
                    return null;
                }
                String truncated = truncate(text);
                return objectMapper.writeValueAsString(truncated);
            }
            Object prepared = input instanceof Map<?, ?> map
                ? prepareMapForLlm(map)
                : stripAugmentKeysDeep(input);
            String serialized = objectMapper.writeValueAsString(prepared);
            return truncateSerialized(serialized);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to serialize genMetadata input", e);
            return null;
        }
    }

    /**
     * Build LLM corpus from a matched JSON object: strip {@code +}-prefixed augment keys and put
     * {@code title}/{@code body} first so {@link #maxInputChars} truncation still sees the
     * classification text when large fields (e.g. GitHub {@code user}) sit between them in
     * Jackson field order.
     */
    private LinkedHashMap<String, Object> prepareMapForLlm(Map<?, ?> map) {
        LinkedHashMap<String, Object> ordered = new LinkedHashMap<>();
        putPreferredTextField(ordered, map, "title");
        putPreferredTextField(ordered, map, "body");
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            if (key.startsWith("+") || "title".equals(key) || "body".equals(key)) {
                continue;
            }
            ordered.put(key, stripAugmentKeysDeep(entry.getValue()));
        }
        return ordered;
    }

    private void putPreferredTextField(LinkedHashMap<String, Object> ordered, Map<?, ?> map,
                                       String key) {
        if (!map.containsKey(key)) {
            return;
        }
        ordered.put(key, stripAugmentKeysDeep(map.get(key)));
    }

    /**
     * Recursively drop {@code +}-prefixed keys so prior augment output is not sent to the model.
     */
    private Object stripAugmentKeysDeep(Object value) {
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> cleaned = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) || key.startsWith("+")) {
                    continue;
                }
                cleaned.put(key, stripAugmentKeysDeep(entry.getValue()));
            }
            return cleaned;
        }
        if (value instanceof List<?> list) {
            List<Object> cleaned = new ArrayList<>(list.size());
            for (Object item : list) {
                cleaned.add(stripAugmentKeysDeep(item));
            }
            return cleaned;
        }
        return value;
    }
    Object parseModelJson(Object raw) {
        return parseModelJson(raw, null);
    }

    Object parseModelJson(Object raw, JsonSchemaFilter outputSchema) {
        if (raw == null) {
            return null;
        }
        Optional<GenMetadataSchemaSupport.ClassifyShape> classify =
            GenMetadataSchemaSupport.classifyShape(outputSchema);
        if (raw instanceof Map<?, ?> map) {
            if (classify.isPresent() && classify.get().isRootString()) {
                return GenMetadataSchemaSupport.labelFromMap(map, classify.get()).orElse(null);
            }
            return toSortedMap(map);
        }
        if (raw instanceof String response) {
            if (classify.isPresent() && classify.get().isRootString()) {
                return parseRootStringLabel(response, classify.get());
            }

            // Prefer valid JSON object when present (including after prose / fences).
            String json = extractJsonObject(response);
            if (json != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = objectMapper.readValue(json, Map.class);
                    return new TreeMap<>(map);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to parse genMetadata JSON response: " + json, e);
                    // Fall through: incomplete JSON may still contain a usable classify label.
                }
            }

            if (classify.isPresent()) {
                Optional<Map<String, Object>> wrapped =
                    GenMetadataSchemaSupport.wrapClassifyLabel(response, classify.get());
                if (wrapped.isPresent()) {
                    return new TreeMap<>(wrapped.get());
                }
                // Truncated JSON / prose with an enum still recoverable.
                Optional<Map<String, Object>> fromText =
                    GenMetadataSchemaSupport.findEnumInText(response, classify.get());
                if (fromText.isPresent()) {
                    return new TreeMap<>(fromText.get());
                }
            }
            return null;
        }
        return null;
    }

    private String parseRootStringLabel(String response,
                                         GenMetadataSchemaSupport.ClassifyShape shape) {
        String trimmed = stripMarkdownFence(response.trim());

        if (trimmed.startsWith("\"")) {
            try {
                String parsed = objectMapper.readValue(trimmed, String.class);
                Optional<String> label = GenMetadataSchemaSupport.recoverLabel(parsed, shape);
                if (label.isPresent()) {
                    return label.get();
                }
            } catch (Exception e) {
                log.log(Level.FINE, "genMetadata string-enum JSON string parse failed", e);
            }
        }

        String json = extractJsonObject(trimmed);
        if (json != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(json, Map.class);
                Optional<String> fromMap = GenMetadataSchemaSupport.labelFromMap(map, shape);
                if (fromMap.isPresent()) {
                    return fromMap.get();
                }
            } catch (Exception e) {
                log.log(Level.FINE, "genMetadata string-enum object fallback parse failed", e);
            }
        }

        return GenMetadataSchemaSupport.recoverLabel(trimmed, shape).orElse(null);
    }

    private String serializeForLog(Object parsed) {
        try {
            return objectMapper.writeValueAsString(parsed);
        } catch (Exception e) {
            return String.valueOf(parsed);
        }
    }

    private static String truncateForLog(String value) {
        if (value == null) {
            return "null";
        }
        if (value.length() <= MAX_LOG_OUTPUT_CHARS) {
            return value;
        }
        return value.substring(0, MAX_LOG_OUTPUT_CHARS) + "... (" + value.length() + " chars total)";
    }

    /**
     * Pull the outermost JSON object from a model response, ignoring markdown fences and leading
     * prose (e.g. {@code Here is the JSON requested: {...}}).
     */
    static String extractJsonObject(String response) {
        if (StringUtils.isBlank(response)) {
            return null;
        }
        String trimmed = stripMarkdownFence(response.trim());
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        // Incomplete object (e.g. truncated mid-generation) — not parseable as JSON.
        return null;
    }

    static String stripMarkdownFence(String response) {
        String trimmed = response.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String withoutOpen = trimmed.substring(firstNewline + 1);
        int closeFence = withoutOpen.lastIndexOf("```");
        if (closeFence >= 0) {
            return withoutOpen.substring(0, closeFence).trim();
        }
        return withoutOpen.trim();
    }

    private TreeMap<String, Object> toSortedMap(Map<?, ?> raw) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        raw.forEach((k, v) -> {
            if (k != null) {
                sorted.put(String.valueOf(k), v);
            }
        });
        return sorted;
    }

    private String truncate(String text) {
        if (text.length() <= maxInputChars) {
            return text;
        }
        return text.substring(0, maxInputChars);
    }

    /**
     * Truncate JSON-serialized non-string inputs (outer quotes included in length budget).
     */
    private String truncateSerialized(String serialized) {
        if (serialized.length() <= maxInputChars) {
            return serialized;
        }
        if (serialized.startsWith("\"") && serialized.endsWith("\"")) {
            int contentBudget = maxInputChars - 2;
            if (contentBudget <= 0) {
                return "\"\"";
            }
            return "\"" + serialized.substring(1, 1 + contentBudget) + "\"";
        }
        return serialized.substring(0, maxInputChars);
    }
}
