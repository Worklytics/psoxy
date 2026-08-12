package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
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
@Builder
public class GenMetadataConfig {

    public static final int DEFAULT_MAX_ATTEMPTS = 2;

    public static final String BACKEND_BEDROCK = "bedrock";
    public static final String BACKEND_VERTEX = "vertex";

    private static final Set<String> SUPPORTED_BACKENDS = Set.of(BACKEND_BEDROCK, BACKEND_VERTEX);

    /** Default Bedrock model id when {@code PSOXY_GEN_MODEL} is unset. */
    public static final String DEFAULT_BEDROCK_MODEL = "anthropic.claude-3-haiku-20240307-v1:0";

    /** Default Vertex Gemini model id when {@code PSOXY_GEN_MODEL} is unset. */
    public static final String DEFAULT_VERTEX_MODEL = "gemini-2.0-flash-001";

    String backend;
    String modelId;
    int timeoutSeconds;
    int maxInputChars;
    int maxTokens;
    /** Total inference attempts per augment (including the first try). */
    @Builder.Default
    int maxAttempts = DEFAULT_MAX_ATTEMPTS;

    public static GenMetadataConfig from(ConfigService configService) {
        String backend = configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_GEN_BACKEND)
            .filter(StringUtils::isNotBlank)
            .map(s -> s.trim().toLowerCase())
            .orElse(BACKEND_BEDROCK);
        String model = configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_GEN_MODEL)
            .filter(StringUtils::isNotBlank)
            .orElseGet(() -> defaultModelForBackend(backend))
            .trim();
        int timeout = configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_GEN_TIMEOUT_SECONDS)
            .flatMap(GenMetadataConfig::parsePositiveInt)
            .orElse(15);
        int maxInput = configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_GEN_MAX_INPUT_CHARS)
            .flatMap(GenMetadataConfig::parsePositiveInt)
            .orElse(4096);
        int maxTokens = configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_GEN_MAX_TOKENS)
            .flatMap(GenMetadataConfig::parsePositiveInt)
            .orElse(256);
        int maxAttempts = configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_GEN_META_RETRIES)
            .flatMap(GenMetadataConfig::parsePositiveInt)
            .orElse(2);
        return GenMetadataConfig.builder()
            .backend(backend)
            .modelId(model)
            .timeoutSeconds(timeout)
            .maxInputChars(maxInput)
            .maxTokens(maxTokens)
            .maxAttempts(maxAttempts)
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
