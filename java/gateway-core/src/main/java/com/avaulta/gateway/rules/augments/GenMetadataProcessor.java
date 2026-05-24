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

    private static volatile GenMetadataBackend backend = new UnavailableGenMetadataBackend();
    private static volatile ObjectMapper objectMapper = new ObjectMapper();
    private static volatile int maxInputChars = DEFAULT_MAX_INPUT_CHARS;

    private GenMetadataProcessor() {
    }

    public static void configure(GenMetadataBackend genMetadataBackend, ObjectMapper mapper,
                                 int maxInputCharsLimit) {
        if (genMetadataBackend != null) {
            backend = genMetadataBackend;
        }
        if (mapper != null) {
            objectMapper = mapper;
        }
        maxInputChars = maxInputCharsLimit > 0 ? maxInputCharsLimit : DEFAULT_MAX_INPUT_CHARS;
    }

    /**
     * @return parsed object suitable for augment output
     * @throws RuntimeException wrapping {@link co.worklytics.psoxy.impl.AugmentProcessingException}
     *         when backend is in core module — use Augment.compute contract via GenMetadataAugmentException
     */
    public static Object process(String taskPrompt, JsonSchemaFilter outputSchema, Object input) {
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

    static String serializeInput(Object input) {
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

    static Map<?, ?> parseModelJson(Object raw) {
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

    private static TreeMap<String, Object> toSortedMap(Map<?, ?> raw) {
        TreeMap<String, Object> sorted = new TreeMap<>();
        raw.forEach((k, v) -> {
            if (k != null) {
                sorted.put(String.valueOf(k), v);
            }
        });
        return sorted;
    }

    private static String truncate(String text) {
        if (text.length() <= maxInputChars) {
            return text;
        }
        return text.substring(0, maxInputChars);
    }

    public static void resetForTests() {
        backend = new UnavailableGenMetadataBackend();
        objectMapper = new ObjectMapper();
        maxInputChars = DEFAULT_MAX_INPUT_CHARS;
    }
}
