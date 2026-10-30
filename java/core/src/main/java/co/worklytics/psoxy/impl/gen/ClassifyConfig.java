package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Deployment configuration for {@code !<classify>} only. {@code GEN_METADATA_*} is not read here.
 *
 * <p>{@link Implementation#REMOTE_LLM} is Bedrock or Vertex, chosen by the host module's
 * {@link GenMetadataConfig}. {@link Implementation#EMBEDDED_DECIDE} scores one choice question
 * on the in-function decider. {@code embedded-llm} and {@code remote-decide} are named and rejected.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClassifyConfig {

    /**
     * Publisher checkpoint id for {@link Implementation#EMBEDDED_DECIDE}. The implementation name
     * in psoxy is {@code embedded-decide}; this string is only the checkpoint.
     */
    public static final String EMBEDDED_CHECKPOINT_ID = "strands-decider-2B-qwen3.5-v1-2610";

    public static final int DEFAULT_TIMEOUT_SECONDS = 15;
    public static final int DEFAULT_MAX_ATTEMPTS = 2;

    /** Choice options the v1 decider server accepts. */
    public static final int MIN_CHOICE_OPTIONS = 2;
    public static final int MAX_CHOICE_OPTIONS = 255;

    private static final Pattern REVISION_TAG = Pattern.compile("[0-9]{6}");

    public enum Implementation {
        REMOTE_LLM("remote-llm"),
        EMBEDDED_DECIDE("embedded-decide"),
        ;

        private final String configValue;

        Implementation(String configValue) {
            this.configValue = configValue;
        }

        public String configValue() {
            return configValue;
        }
    }

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        CLASSIFY_IMPLEMENTATION,
        CLASSIFY_MODEL,
        CLASSIFY_MODEL_REVISION,
        CLASSIFY_TIMEOUT_SECONDS,
        CLASSIFY_RETRIES,
        ;
    }

    private final Implementation implementation;
    /**
     * Remote model id when set. {@code null} on {@link Implementation#REMOTE_LLM} means the host
     * {@link GenMetadataConfig#getModelId()} default. Always {@link #EMBEDDED_CHECKPOINT_ID} for
     * {@link Implementation#EMBEDDED_DECIDE}.
     */
    private final String modelId;
    /** Hub date tag ({@code YYMMDD}) for the embedded checkpoint. {@code null} for remote-llm. */
    private final String modelRevision;
    private final int timeoutSeconds;
    private final int maxAttempts;

    public static ClassifyConfig from(ConfigService configService) {
        String rawImplementation = configService
            .getConfigPropertyAsOptional(ConfigProperty.CLASSIFY_IMPLEMENTATION)
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .orElse(Implementation.REMOTE_LLM.configValue());
        Implementation implementation = parseImplementation(rawImplementation);
        int timeoutSeconds = positiveIntOrDefault(configService,
            ConfigProperty.CLASSIFY_TIMEOUT_SECONDS, DEFAULT_TIMEOUT_SECONDS);
        int maxAttempts = positiveIntOrDefault(configService,
            ConfigProperty.CLASSIFY_RETRIES, DEFAULT_MAX_ATTEMPTS);
        String model = configService.getConfigPropertyAsOptional(ConfigProperty.CLASSIFY_MODEL)
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .orElse(null);
        if (implementation == Implementation.REMOTE_LLM) {
            return new ClassifyConfig(implementation, model, null, timeoutSeconds, maxAttempts);
        }
        String revision = configService
            .getConfigPropertyAsOptional(ConfigProperty.CLASSIFY_MODEL_REVISION)
            .map(String::trim)
            .filter(StringUtils::isNotBlank)
            .orElse(null);
        requireRevision(revision);
        if (model == null) {
            model = EMBEDDED_CHECKPOINT_ID;
        }
        if (!EMBEDDED_CHECKPOINT_ID.equals(model)) {
            throw new IllegalArgumentException(
                "CLASSIFY_MODEL for embedded-decide must be " + EMBEDDED_CHECKPOINT_ID
                    + ", got '" + model + "'");
        }
        return new ClassifyConfig(implementation, model, revision, timeoutSeconds, maxAttempts);
    }

    /**
     * Remote-llm config that follows a host genMetadata config. Used when a test constructs the
     * chat backend without a separate classify deployment.
     */
    public static ClassifyConfig matchingHost(GenMetadataConfig host) {
        return new ClassifyConfig(
            Implementation.REMOTE_LLM,
            host.getModelId(),
            null,
            host.getTimeoutSeconds(),
            host.getMaxAttempts());
    }

    /**
     * Model id the remote chat client should use. Blank {@link #modelId} keeps the host default.
     */
    public String remoteModelId(String hostModelId) {
        if (implementation != Implementation.REMOTE_LLM) {
            throw new IllegalStateException(
                "remote model id is only used for CLASSIFY_IMPLEMENTATION=remote-llm");
        }
        if (StringUtils.isNotBlank(modelId)) {
            return modelId;
        }
        return hostModelId;
    }

    private static Implementation parseImplementation(String raw) {
        if ("embedded-llm".equals(raw) || "remote-decide".equals(raw)) {
            throw new IllegalArgumentException(
                "CLASSIFY_IMPLEMENTATION=" + raw + " is not available");
        }
        if (Implementation.REMOTE_LLM.configValue().equals(raw)) {
            return Implementation.REMOTE_LLM;
        }
        if (Implementation.EMBEDDED_DECIDE.configValue().equals(raw)) {
            return Implementation.EMBEDDED_DECIDE;
        }
        throw new IllegalArgumentException("Unknown CLASSIFY_IMPLEMENTATION: " + raw);
    }

    private static void requireRevision(String revision) {
        if (revision == null) {
            throw new IllegalArgumentException(
                "CLASSIFY_MODEL_REVISION is required for embedded-decide");
        }
        if ("main".equalsIgnoreCase(revision)) {
            throw new IllegalArgumentException(
                "CLASSIFY_MODEL_REVISION must be a YYMMDD tag, not main");
        }
        if (!REVISION_TAG.matcher(revision).matches()) {
            throw new IllegalArgumentException(
                "CLASSIFY_MODEL_REVISION must be a YYMMDD tag, got '" + revision + "'");
        }
    }

    private static int positiveIntOrDefault(ConfigService configService,
                                            ConfigService.ConfigProperty property,
                                            int defaultValue) {
        return configService.getConfigPropertyAsOptional(property)
            .filter(StringUtils::isNotBlank)
            .map(value -> ConfigService.parseIntValue(property, value))
            .filter(value -> value > 0)
            .orElse(defaultValue);
    }
}
