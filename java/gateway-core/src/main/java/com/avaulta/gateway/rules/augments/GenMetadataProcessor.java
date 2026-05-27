package com.avaulta.gateway.rules.augments;

import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Facade for {@link Augment.GenMetadata} — delegates to a configured {@link GenMetadataBackend}.
 */
public class GenMetadataProcessor {

    private static final Logger log = Logger.getLogger(GenMetadataProcessor.class.getName());

    private static final int DEFAULT_MAX_INPUT_CHARS = 4096;

    private final GenMetadataBackend backend;
    private final ObjectMapper objectMapper;
    private final int maxInputChars;

    public GenMetadataProcessor(GenMetadataBackend backend, ObjectMapper objectMapper, int maxInputChars) {
        this.backend = backend != null ? backend : new UnavailableGenMetadataBackend();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.maxInputChars = maxInputChars > 0 ? maxInputChars : DEFAULT_MAX_INPUT_CHARS;
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
            Object raw = backend.generate(taskPrompt, outputSchema, inputJson);
            Map<?, ?> parsed = parseModelJson(raw);
            if (parsed == null) {
                throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.INFERENCE_FAILED,
                    "genMetadata backend returned unparseable output");
            }
            return parsed;
        } catch (GenMetadataAugmentException e) {
            throw e;
        } catch (Exception e) {
            log.log(Level.WARNING, "genMetadata inference failed", e);
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.INFERENCE_FAILED,
                "genMetadata inference failed", e);
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
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to serialize genMetadata input", e);
            return null;
        }
    }

    Map<?, ?> parseModelJson(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Map<?, ?> map) {
            return toSortedMap(map);
        }
        if (raw instanceof String response) {
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
}
