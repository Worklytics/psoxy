package com.avaulta.gateway.rules.augments;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Gemini thinking-level values for {@link Augment.GenMetadata#getThinkingLevel()}.
 *
 * <p>YAML may use lowercase ({@code minimal}); Vertex expects uppercase ({@code MINIMAL}).
 * Bedrock ignores this setting.
 */
public final class GenMetadataThinkingLevels {

    private static final Logger log = Logger.getLogger(GenMetadataThinkingLevels.class.getName());

    public static final String MINIMAL = "MINIMAL";
    public static final String LOW = "LOW";
    public static final String MEDIUM = "MEDIUM";
    public static final String HIGH = "HIGH";

    /** Default when omitted from rules / null / blank. */
    public static final String DEFAULT = MINIMAL;

    private static final Set<String> ALLOWED = Set.of(MINIMAL, LOW, MEDIUM, HIGH);

    private GenMetadataThinkingLevels() {
    }

    /**
     * Normalize a rule or config value to a Vertex API thinking level.
     * Unknown values fall back to {@link #DEFAULT} with a warning.
     */
    public static String resolve(String raw) {
        if (StringUtils.isBlank(raw)) {
            return DEFAULT;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if (ALLOWED.contains(normalized)) {
            return normalized;
        }
        log.warning("genMetadata thinkingLevel '" + raw + "' is not one of "
            + ALLOWED + "; using " + DEFAULT);
        return DEFAULT;
    }
}
