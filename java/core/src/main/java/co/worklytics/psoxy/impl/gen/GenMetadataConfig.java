package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.Optional;

/**
 * Deployment configuration for genMetadata. Construct via {@link #from(ConfigService)}; env keys
 * live on {@link ConfigProperty} / platform subclasses, not {@code ProxyConfigProperty}.
 *
 * <p>Cloud-only: {@link Backend#BEDROCK} (AWS) or {@link Backend#VERTEX} (GCP).
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class GenMetadataConfig {

    public static final int DEFAULT_MAX_ATTEMPTS = 2;
    public static final int DEFAULT_TIMEOUT_SECONDS = 15;

    public enum Backend {
        BEDROCK,
        VERTEX,
        ;

        public String configValue() {
            return name().toLowerCase(Locale.ROOT);
        }

        static Optional<Backend> fromConfigValue(String raw) {
            if (StringUtils.isBlank(raw)) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return Optional.empty();
            }
        }
    }

    /**
     * Shared genMetadata env keys. Vertex-only keys live on
     * {@link VertexGenMetadataConfig.ConfigProperty}.
     */
    public enum ConfigProperty implements ConfigService.ConfigProperty {
        /** {@code bedrock} or {@code vertex}. Unset defaults to Bedrock. */
        GEN_METADATA_BACKEND,
        GEN_METADATA_MODEL,
        GEN_METADATA_TIMEOUT_SECONDS,
        GEN_METADATA_RETRIES,
        /** Set by Terraform {@code enable_gen_metadata = true}. */
        ENABLE_GEN_METADATA,
        ;
    }

    private final Backend backend;
    private final String modelId;
    private final int timeoutSeconds;
    private final int maxAttempts;

    public static GenMetadataConfig from(ConfigService configService) {
        String rawBackend = configService.getConfigPropertyAsOptional(ConfigProperty.GEN_METADATA_BACKEND)
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .orElse(null);
        Optional<Backend> parsed = Backend.fromConfigValue(rawBackend);
        if (rawBackend != null && parsed.isEmpty()) {
            return new Unsupported(rawBackend);
        }
        Backend backend = parsed.orElse(Backend.BEDROCK);
        return switch (backend) {
            case BEDROCK -> BedrockGenMetadataConfig.from(configService);
            case VERTEX -> VertexGenMetadataConfig.from(configService);
        };
    }

    public boolean isSupportedCloudBackend() {
        return backend == Backend.BEDROCK || backend == Backend.VERTEX;
    }

    static String optionalModelId(ConfigService configService) {
        return configService.getConfigPropertyAsOptional(ConfigProperty.GEN_METADATA_MODEL)
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .orElse(null);
    }

    static int timeoutSeconds(ConfigService configService) {
        return positiveIntOrDefault(configService, ConfigProperty.GEN_METADATA_TIMEOUT_SECONDS,
            DEFAULT_TIMEOUT_SECONDS);
    }

    static int maxAttempts(ConfigService configService) {
        return positiveIntOrDefault(configService, ConfigProperty.GEN_METADATA_RETRIES,
            DEFAULT_MAX_ATTEMPTS);
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

    /**
     * Unknown {@code GEN_METADATA_BACKEND} value — no cloud provider will match.
     */
    @Getter
    public static final class Unsupported extends GenMetadataConfig {

        private final String rawBackend;

        public Unsupported(String rawBackend) {
            super(null, "unsupported", DEFAULT_TIMEOUT_SECONDS, DEFAULT_MAX_ATTEMPTS);
            this.rawBackend = rawBackend;
        }

        @Override
        public boolean isSupportedCloudBackend() {
            return false;
        }
    }
}
