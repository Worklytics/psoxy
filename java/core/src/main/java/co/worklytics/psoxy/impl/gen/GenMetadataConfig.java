package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.avaulta.gateway.rules.augments.GenMetadataThinkingLevels;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;
import java.util.Set;

/**
 * Deployment configuration for {@link com.avaulta.gateway.rules.augments.GenMetadataBackend}.
 *
 * <p>Cloud-only: {@code bedrock} (AWS) or {@code vertex} (GCP). Local/Jlama is not supported.
 */
@Value
@Builder(toBuilder = true)
public class GenMetadataConfig {

    public static final int DEFAULT_MAX_ATTEMPTS = 2;

    /**
     * Default {@code METADATA_GEN_MAX_TOKENS}. Gemini 3.x thinking tokens share this budget with
     * visible output; 256 was too tight when thinking ran at the model default. With
     * {@code thinkingLevel=MINIMAL} on Vertex, 1024 still leaves headroom for small JSON.
     */
    public static final int DEFAULT_MAX_TOKENS = 1024;

    public static final String BACKEND_BEDROCK = "bedrock";
    public static final String BACKEND_VERTEX = "vertex";

    private static final Set<String> SUPPORTED_BACKENDS = Set.of(BACKEND_BEDROCK, BACKEND_VERTEX);

    /** Default Bedrock model id when {@code METADATA_GEN_MODEL} is unset. */
    public static final String DEFAULT_BEDROCK_MODEL = "anthropic.claude-3-haiku-20240307-v1:0";

    /** Default Vertex Gemini model id when {@code METADATA_GEN_MODEL} is unset. */
    public static final String DEFAULT_VERTEX_MODEL = "gemini-3.5-flash-lite";

    /**
     * Default Vertex location when {@code METADATA_GEN_MODEL_REGION} is unset.
     * Matches {@link #DEFAULT_VERTEX_MODEL}. Prefer {@code global}. The google-genai client maps
     * {@code global} to {@code https://aiplatform.googleapis.com}; multi-region {@code us}/{@code eu}
     * use regional publisher hosts. Avoid inventing hostnames like {@code us-aiplatform.googleapis.com}.
     */
    public static final String DEFAULT_VERTEX_MODEL_REGION = "global";

    String backend;
    String modelId;
    /**
     * Vertex AI location for the publisher model endpoint ({@code global}, {@code us}, regional id, …).
     * Null when unset / non-Vertex; Vertex provider applies {@link #DEFAULT_VERTEX_MODEL_REGION}.
     */
    String modelRegion;
    int timeoutSeconds;
    int maxInputChars;
    int maxTokens;
    /** Total inference attempts per augment (including the first try). */
    @Builder.Default
    int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    /**
     * Per-deployment Vertex Gemini thinking level ({@code MINIMAL}|{@code LOW}|{@code MEDIUM}|{@code HIGH}).
     * From {@code METADATA_GEN_THINKING_LEVEL}; ignored by Bedrock. Null → {@code MINIMAL} at provider.
     */
    String thinkingLevel;

    public static GenMetadataConfig from(ConfigService configService) {
        String backend = configService.getConfigPropertyAsOptional(ProxyConfigProperty.METADATA_GEN_BACKEND)
            .filter(StringUtils::isNotBlank)
            .map(s -> s.trim().toLowerCase())
            .orElse(BACKEND_BEDROCK);
        String model = configService.getConfigPropertyAsOptional(ProxyConfigProperty.METADATA_GEN_MODEL)
            .filter(StringUtils::isNotBlank)
            .orElseGet(() -> defaultModelForBackend(backend))
            .trim();
        String modelRegion = configService.getConfigPropertyAsOptional(ProxyConfigProperty.METADATA_GEN_MODEL_REGION)
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .orElseGet(() -> BACKEND_VERTEX.equalsIgnoreCase(backend) ? DEFAULT_VERTEX_MODEL_REGION : null);
        int timeout = configService.getConfigPropertyAsOptional(ProxyConfigProperty.METADATA_GEN_TIMEOUT_SECONDS)
            .flatMap(GenMetadataConfig::parsePositiveInt)
            .orElse(15);
        int maxInput = configService.getConfigPropertyAsOptional(ProxyConfigProperty.METADATA_GEN_MAX_INPUT_CHARS)
            .flatMap(GenMetadataConfig::parsePositiveInt)
            .orElse(4096);
        int maxTokens = configService.getConfigPropertyAsOptional(ProxyConfigProperty.METADATA_GEN_MAX_TOKENS)
            .flatMap(GenMetadataConfig::parsePositiveInt)
            .orElse(DEFAULT_MAX_TOKENS);
        int maxAttempts = configService.getConfigPropertyAsOptional(ProxyConfigProperty.METADATA_GEN_RETRIES)
            .flatMap(GenMetadataConfig::parsePositiveInt)
            .orElse(2);
        String thinkingLevel = configService.getConfigPropertyAsOptional(ProxyConfigProperty.METADATA_GEN_THINKING_LEVEL)
            .filter(StringUtils::isNotBlank)
            .map(GenMetadataThinkingLevels::resolve)
            .orElse(GenMetadataThinkingLevels.DEFAULT);
        return GenMetadataConfig.builder()
            .backend(backend)
            .modelId(model)
            .modelRegion(modelRegion)
            .timeoutSeconds(timeout)
            .maxInputChars(maxInput)
            .maxTokens(maxTokens)
            .maxAttempts(maxAttempts)
            .thinkingLevel(thinkingLevel)
            .build();
    }

    static String defaultModelForBackend(String backend) {
        if (BACKEND_VERTEX.equalsIgnoreCase(backend)) {
            return DEFAULT_VERTEX_MODEL;
        }
        return DEFAULT_BEDROCK_MODEL;
    }

    public boolean isSupportedCloudBackend() {
        return backend != null && SUPPORTED_BACKENDS.contains(backend.toLowerCase());
    }

    private static Optional<Integer> parsePositiveInt(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? Optional.of(v) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
