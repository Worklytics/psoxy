package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.JsonSchemaValidationUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
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
                Map<?, ?> parsed = inferOnce(taskPrompt, outputSchema, inputJson);
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

    private Map<?, ?> inferOnce(String taskPrompt, JsonSchemaFilter outputSchema, String inputJson)
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
        Map<?, ?> parsed = parseModelJson(raw, outputSchema);
        if (parsed == null) {
            log.warning("genMetadata backend returned unparseable output");
            return null;
        }
        log.info("genMetadata parsed output keys: " + parsed.keySet()
            + "; value=" + truncateForLog(serializeForLog(parsed)));
        return parsed;
    }

    private boolean validatesOutputSchema(Map<?, ?> parsed, JsonSchemaFilter outputSchema) {
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
            String serialized = objectMapper.writeValueAsString(input);
            return truncateSerialized(serialized);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to serialize genMetadata input", e);
            return null;
        }
    }

    Map<?, ?> parseModelJson(Object raw) {
        return parseModelJson(raw, null);
    }

    Map<?, ?> parseModelJson(Object raw, JsonSchemaFilter outputSchema) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            return toSortedMap(map);
        }
        if (raw instanceof String response) {
            Optional<GenMetadataSchemaSupport.ClassifyShape> classify =
                GenMetadataSchemaSupport.classifyShape(outputSchema);
            if (classify.isPresent()) {
                Optional<Map<String, Object>> wrapped =
                    GenMetadataSchemaSupport.wrapClassifyLabel(response, classify.get());
                if (wrapped.isPresent()) {
                    return new TreeMap<>(wrapped.get());
                }
            }
            String json = extractJsonObject(response);
            if (json == null) {
                return null;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = objectMapper.readValue(json, Map.class);
                return new TreeMap<>(map);
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to parse genMetadata JSON response: " + json, e);
                return null;
            }
        }
        return null;
    }

    private String serializeForLog(Map<?, ?> parsed) {
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

    static String extractJsonObject(String response) {
        if (StringUtils.isBlank(response)) {
            return null;
        }
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed.startsWith("{") ? trimmed : null;
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
