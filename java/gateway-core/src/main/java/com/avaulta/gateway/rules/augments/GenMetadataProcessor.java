package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchema;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
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

    private static final int DEFAULT_MAX_ATTEMPTS = 2;

    private final GenMetadataBackend backend;
    private final ObjectMapper objectMapper;
    private final JsonSchemaValidationUtils jsonSchemaValidationUtils;
    private final int maxAttempts;

    public GenMetadataProcessor(GenMetadataBackend backend, ObjectMapper objectMapper,
                                int maxAttempts, JsonSchemaValidationUtils jsonSchemaValidationUtils) {
        this.backend = backend;
        this.objectMapper = objectMapper;
        this.jsonSchemaValidationUtils = jsonSchemaValidationUtils;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
    }

    /**
     * Compute genMetadata output for a single augment invocation.
     */
    public Object compute(Augment.GenMetadata augment, Object input) {
        return process(augment.getPrompt(), augment.getOutputSchema(), input,
            augment.getMaxOutputTokens(), augment.getMaxInputTokens());
    }

    public Object process(String taskPrompt, JsonSchema outputSchema, Object input) {
        return process(taskPrompt, outputSchema, input, null, null);
    }

    public Object process(String taskPrompt, JsonSchema outputSchema, Object input,
                          Integer maxOutputTokens, Integer maxInputTokens) {
        if (StringUtils.isBlank(taskPrompt) || outputSchema == null) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "genMetadata missing prompt or outputSchema");
        }
        String inputJson = serializeInput(input);
        if (inputJson == null) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "genMetadata input empty or not serializable");
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                log.info("genMetadata inference retry attempt " + attempt + " of " + maxAttempts);
            }
            Object parsed = inferOnce(taskPrompt, outputSchema, inputJson,
                maxOutputTokens, maxInputTokens);
            if (parsed != null && validatesOutputSchema(parsed, outputSchema)) {
                return parsed;
            }
        }
        throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.INFERENCE_FAILED,
            "genMetadata output failed schema validation after " + maxAttempts + " attempt(s)");
    }

    private Object inferOnce(String taskPrompt, JsonSchema outputSchema, String inputJson,
                             Integer maxOutputTokens, Integer maxInputTokens) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        log.info("genMetadata augment inference call started at " + startedAt);
        Object raw;
        try {
            raw = backend.generate(taskPrompt, outputSchema, inputJson,
                maxOutputTokens, maxInputTokens);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            log.info("genMetadata augment inference call completed in " + elapsedMs + "ms");
        }
        if (raw instanceof String rawText) {
            log.info("genMetadata backend response received chars=" + rawText.length());
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
            log.info("genMetadata parsed output keys: " + map.keySet());
        } else if (parsed instanceof List<?> list) {
            log.info("genMetadata parsed output array size: " + list.size());
        } else {
            log.info("genMetadata parsed output type: " + parsed.getClass().getSimpleName());
        }
        return parsed;
    }

    private boolean validatesOutputSchema(Object parsed, JsonSchema outputSchema) {
        String json;
        try {
            json = objectMapper.writeValueAsString(parsed);
        } catch (JsonProcessingException e) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.INFERENCE_FAILED,
                "Failed to serialize genMetadata output for validation", e);
        }
        return jsonSchemaValidationUtils.validateJsonBySchema(json, outputSchema);
    }

    String serializeInput(Object input) {
        if (input == null) {
            return null;
        }
        try {
            if (input instanceof String text) {
                return text.isEmpty() ? null : text;
            }
            return objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            log.log(Level.WARNING, "Failed to serialize genMetadata input", e);
            return null;
        }
    }

    Object parseModelJson(Object raw, JsonSchema outputSchema) {
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
        if (raw instanceof List<?> list) {
            return list;
        }
        if (raw instanceof String response) {
            if (classify.isPresent() && classify.get().isRootString()) {
                return parseRootStringLabel(response, classify.get());
            }

            String json = extractJsonValue(response);
            if (json != null) {
                try {
                    Object parsed = objectMapper.readValue(json, Object.class);
                    if (parsed instanceof Map<?, ?> map) {
                        if (classify.isPresent() && classify.get().isRootString()) {
                            return GenMetadataSchemaSupport.labelFromMap(map, classify.get())
                                .orElse(null);
                        }
                        return toSortedMap(map);
                    }
                    if (parsed instanceof List<?>) {
                        return parsed;
                    }
                    if (classify.isEmpty()) {
                        return parsed;
                    }
                } catch (JsonProcessingException e) {
                    log.log(Level.WARNING,
                        "Failed to parse genMetadata JSON response (" + json.length() + " chars)", e);
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
            } catch (JsonProcessingException e) {
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
            } catch (JsonProcessingException e) {
                log.log(Level.FINE, "genMetadata string-enum object fallback parse failed", e);
            }
        }

        return GenMetadataSchemaSupport.recoverLabel(trimmed, shape).orElse(null);
    }

    /**
     * Pull the outermost JSON value from a model response, ignoring markdown fences and leading
     * prose (e.g. {@code Here is the JSON requested: {...}}). Objects, arrays, and JSON scalars
     * are all eligible.
     */
    String extractJsonValue(String response) {
        if (StringUtils.isBlank(response)) {
            return null;
        }
        String trimmed = stripMarkdownFence(response.trim());
        if (isJson(trimmed)) {
            return trimmed;
        }
        int objStart = trimmed.indexOf('{');
        int arrStart = trimmed.indexOf('[');
        if (objStart < 0 && arrStart < 0) {
            return null;
        }
        boolean arrayFirst = arrStart >= 0 && (objStart < 0 || arrStart < objStart);
        if (arrayFirst) {
            int end = trimmed.lastIndexOf(']');
            if (end > arrStart) {
                String candidate = trimmed.substring(arrStart, end + 1);
                return isJson(candidate) ? candidate : null;
            }
            return null;
        }
        int end = trimmed.lastIndexOf('}');
        if (end > objStart) {
            String candidate = trimmed.substring(objStart, end + 1);
            return isJson(candidate) ? candidate : null;
        }
        return null;
    }

    private boolean isJson(String text) {
        try {
            objectMapper.readTree(text);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * Pull the outermost JSON object from a model response, ignoring markdown fences and leading
     * prose (e.g. {@code Here is the JSON requested: {...}}).
     */
    String extractJsonObject(String response) {
        String json = extractJsonValue(response);
        if (json != null && json.startsWith("{")) {
            return json;
        }
        return null;
    }

    String stripMarkdownFence(String response) {
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
}
