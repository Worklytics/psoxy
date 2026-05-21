package co.worklytics.psoxy.gateway;

import java.util.Optional;

import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Value;

/**
 * POJO collecting all configuration values for remote resource resolution (S3/GCS).
 *
 * <p>A single {@code REMOTE_RESOURCE_BUCKET} is shared across both instance-specific resources
 * (e.g., custom rules) and shared resources (e.g., NLP/LLM models), differentiated by path prefix.</p>
 */
@Value
@Builder
public class RemoteResourceConfig {

    /**
     * Bucket name (S3 or GCS) for remote resources. If absent, remote resource loading is disabled.
     */
    String bucket;

    /**
     * Path prefix for instance-specific resources (e.g., custom rules).
     * Derived from {@code INSTANCE_RESOURCE_PATH}, falling back to {@code PATH_TO_INSTANCE_CONFIG}.
     */
    String instanceResourcePath;

    /**
     * Path prefix for shared resources (e.g., NLP models, LLMs).
     * Derived from {@code SHARED_RESOURCE_PATH}, falling back to {@code PATH_TO_SHARED_CONFIG}.
     */
    String sharedResourcePath;

    /**
     * @return bucket if configured
     */
    public Optional<String> getBucket() {
        return Optional.ofNullable(bucket);
    }

    /**
     * @return instance resource path if configured
     */
    public Optional<String> getInstanceResourcePath() {
        return Optional.ofNullable(instanceResourcePath);
    }

    /**
     * @return shared resource path if configured
     */
    public Optional<String> getSharedResourcePath() {
        return Optional.ofNullable(sharedResourcePath);
    }

    /**
     * Factory method to build config from ConfigService.
     *
     * <p>This centralizes all the config resolution logic so platform modules (AWS/GCP) don't
     * need to duplicate it.</p>
     *
     * @param envVarsConfigService env vars config service (these properties are env-var-only)
     * @param defaultInstancePath platform-specific default for instance resource path (e.g., derived
     *                            from function name / namespace), used only if neither
     *                            {@code INSTANCE_RESOURCE_PATH} nor {@code PATH_TO_INSTANCE_CONFIG} is set
     */
    public static RemoteResourceConfig fromConfigService(EnvVarsConfigService envVarsConfigService,
                                                          String defaultInstancePath) {
        RemoteResourceConfigBuilder builder = RemoteResourceConfig.builder();

        envVarsConfigService.getConfigPropertyAsOptional(ConfigProperty.REMOTE_RESOURCE_BUCKET)
            .ifPresent(builder::bucket);

        // instance path: explicit > PATH_TO_INSTANCE_CONFIG > platform default
        builder.instanceResourcePath(
            envVarsConfigService.getConfigPropertyAsOptional(ConfigProperty.INSTANCE_RESOURCE_PATH)
                .orElseGet(() -> envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                    .orElse(defaultInstancePath)));

        // shared path: explicit > PATH_TO_SHARED_CONFIG
        builder.sharedResourcePath(
            envVarsConfigService.getConfigPropertyAsOptional(ConfigProperty.SHARED_RESOURCE_PATH)
                .orElseGet(() -> envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                    .orElse(null)));

        return builder.build();
    }

    @AllArgsConstructor
    @Getter
    public enum ConfigProperty implements ConfigService.ConfigProperty {

        /**
         * Bucket (S3 or GCS) from which to load resources.
         */
        REMOTE_RESOURCE_BUCKET,

        /**
         * Path prefix within the bucket for instance-specific resources (e.g., custom rules).
         */
        INSTANCE_RESOURCE_PATH,

        /**
         * Path prefix within the bucket for shared resources (e.g., NLP models, LLMs).
         */
        SHARED_RESOURCE_PATH,
        ;
    }
}
