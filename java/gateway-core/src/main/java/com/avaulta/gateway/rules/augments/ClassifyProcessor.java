package com.avaulta.gateway.rules.augments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Facade for {@link Augment.Classify} — closed-set label via {@link GenMetadataBackend}.
 */
public class ClassifyProcessor {

    private static final Logger log = Logger.getLogger(ClassifyProcessor.class.getName());

    private static final int DEFAULT_MAX_ATTEMPTS = 2;

    private final GenMetadataBackend backend;
    private final ObjectMapper objectMapper;
    private final int maxAttempts;

    public ClassifyProcessor(GenMetadataBackend backend, ObjectMapper objectMapper, int maxAttempts) {
        this.backend = backend;
        this.objectMapper = objectMapper;
        this.maxAttempts = maxAttempts > 0 ? maxAttempts : DEFAULT_MAX_ATTEMPTS;
    }

    public Object compute(Augment.Classify augment, Object input) {
        if (StringUtils.isBlank(augment.getPrompt()) || augment.getClasses() == null
            || augment.getClasses().isEmpty()) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "classify missing prompt or classes");
        }
        String inputJson = serializeInput(input);
        if (inputJson == null) {
            throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.UNAVAILABLE,
                "classify input empty or not serializable");
        }
        GenMetadataInferenceOptions options = GenMetadataInferenceOptions.builder()
            .maxOutputTokens(augment.getMaxOutputTokens() + Augment.Classify.MAX_OUTPUT_TOKEN_SLACK)
            .maxInputTokens(augment.getMaxInputTokens())
            .build();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                log.info("classify inference retry attempt " + attempt + " of " + maxAttempts);
            }
            String label = inferOnce(augment.getPrompt(), augment.getClasses(), inputJson, options);
            if (label != null) {
                return label;
            }
        }
        throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.INFERENCE_FAILED,
            "classify output was not exactly one of the allowed classes after "
                + maxAttempts + " attempt(s)");
    }

    private String inferOnce(String taskPrompt, List<String> classes, String inputJson,
                             GenMetadataInferenceOptions options) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        log.info("classify augment inference call started at " + startedAt);
        GenMetadataInferenceResult result;
        try {
            result = backend.classify(taskPrompt, classes, inputJson, options);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            log.info("classify augment inference call completed in " + elapsedMs + "ms");
        }
        String label = findClass(result == null ? null : result.getText(), classes);
        if (label == null) {
            log.warning("classify backend response did not contain exactly one allowed class");
        }
        return label;
    }

    /**
     * Resolve {@code raw} to one configured class: whole-string / JSON-string exact match first,
     * then a token-boundary search (so {@code A} cannot match inside {@code Answer}). Longer
     * bounded matches win ({@code Email Drafting} beats {@code Email}); equal length prefers the
     * earlier occurrence.
     */
    String findClass(Object raw, List<String> classes) {
        if (raw == null || classes == null || classes.isEmpty()) {
            return null;
        }
        String text = raw instanceof String s ? s : String.valueOf(raw);
        String exact = matchExactClass(text, classes);
        if (exact != null) {
            return exact;
        }
        return matchBoundedClass(text, classes);
    }

    private String matchExactClass(String text, List<String> classes) {
        String trimmedText = text.trim();
        String exact = firstEqualClass(trimmedText, classes);
        if (exact != null) {
            return exact;
        }
        String unquoted = unquoteJsonString(trimmedText);
        if (unquoted == null) {
            return null;
        }
        return firstEqualClass(unquoted, classes);
    }

    private String firstEqualClass(String value, List<String> classes) {
        for (String candidate : classes) {
            String trimmed = StringUtils.trimToNull(candidate);
            if (trimmed != null && trimmed.equals(value)) {
                return trimmed;
            }
        }
        return null;
    }

    private String unquoteJsonString(String trimmedText) {
        if (trimmedText.length() < 2 || trimmedText.charAt(0) != '"') {
            return null;
        }
        try {
            return objectMapper.readValue(trimmedText, String.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String matchBoundedClass(String text, List<String> classes) {
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (String candidate : classes) {
            String trimmed = StringUtils.trimToNull(candidate);
            if (trimmed == null) {
                continue;
            }
            Matcher matcher = Pattern.compile("(?<!\\w)" + Pattern.quote(trimmed) + "(?!\\w)")
                .matcher(text);
            if (!matcher.find()) {
                continue;
            }
            int index = matcher.start();
            if (best == null
                || trimmed.length() > best.length()
                || (trimmed.length() == best.length() && index < bestIndex)) {
                best = trimmed;
                bestIndex = index;
            }
        }
        return best;
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
            log.log(Level.WARNING, "Failed to serialize classify input", e);
            return null;
        }
    }
}
