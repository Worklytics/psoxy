package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

/**
 * Deployment configuration for {@link com.avaulta.gateway.rules.augments.GenMetadataBackend}.
 */
@Value
@Builder
public class GenMetadataConfig {

    public static final String BACKEND_LOCAL = "local";
    public static final String DEFAULT_MODEL = "llama-3.2-1b-instruct";

    String backend;
    String modelId;
    int timeoutSeconds;
    int maxInputChars;
    int maxTokens;

    public static GenMetadataConfig from(ConfigService configService) {
        String backend = configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_GEN_BACKEND)
            .filter(StringUtils::isNotBlank)
            .orElse(BACKEND_LOCAL)
            .trim()
            .toLowerCase();
        String model = configService.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_GEN_MODEL)
            .filter(StringUtils::isNotBlank)
            .orElse(DEFAULT_MODEL)
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
        return GenMetadataConfig.builder()
            .backend(backend)
            .modelId(model)
            .timeoutSeconds(timeout)
            .maxInputChars(maxInput)
            .maxTokens(maxTokens)
            .build();
    }

    private static Optional<Integer> parsePositiveInt(String raw) {
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? Optional.of(v) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public String localModelObjectPath() {
        return "llm/" + modelId + ".gguf";
    }
}
