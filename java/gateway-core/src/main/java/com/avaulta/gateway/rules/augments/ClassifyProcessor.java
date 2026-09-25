package com.avaulta.gateway.rules.augments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        int maxOutputTokens = augment.getMaxOutputTokens();
        int maxInputTokens = augment.getMaxInputTokens();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                log.info("classify inference retry attempt " + attempt + " of " + maxAttempts);
            }
            String label = inferOnce(augment.getPrompt(), augment.getClasses(), inputJson,
                maxOutputTokens, maxInputTokens);
            if (label != null) {
                return label;
            }
        }
        throw new GenMetadataAugmentException(GenMetadataAugmentException.Code.INFERENCE_FAILED,
            "classify output was not exactly one of the allowed classes after "
                + maxAttempts + " attempt(s)");
    }

    private String inferOnce(String taskPrompt, List<String> classes, String inputJson,
                             int maxOutputTokens, int maxInputTokens) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        log.info("classify augment inference call started at " + startedAt);
        Object raw;
        try {
            raw = backend.classify(taskPrompt, classes, inputJson, maxOutputTokens, maxInputTokens);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
            log.info("classify augment inference call completed in " + elapsedMs + "ms");
        }
        String label = findClass(raw, classes);
        if (label == null) {
            log.warning("classify backend response did not contain exactly one allowed class");
        }
        return label;
    }

    /**
     * If {@code raw} contains any class name as an exact substring, return that class.
     * Longer matches win (so {@code Email Drafting} beats {@code Email}); equal length prefers
     * the earlier occurrence.
     */
    String findClass(Object raw, List<String> classes) {
        if (raw == null || classes == null || classes.isEmpty()) {
            return null;
        }
        String text = raw instanceof String s ? s : String.valueOf(raw);
        String best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (String candidate : classes) {
            if (StringUtils.isBlank(candidate)) {
                continue;
            }
            int index = text.indexOf(candidate);
            if (index < 0) {
                continue;
            }
            if (best == null
                || candidate.length() > best.length()
                || (candidate.length() == best.length() && index < bestIndex)) {
                best = candidate;
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
