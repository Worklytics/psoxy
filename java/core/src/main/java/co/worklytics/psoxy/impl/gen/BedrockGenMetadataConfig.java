package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import org.apache.commons.lang3.StringUtils;

/**
 * Amazon Bedrock deployment config. Model default and Nova inference-profile rewrite live here,
 * not in shared / Vertex code.
 */
public final class BedrockGenMetadataConfig extends GenMetadataConfig {

    /** Default when {@link ConfigProperty#GEN_METADATA_MODEL} is unset (US cross-region inference profile). */
    public static final String DEFAULT_MODEL = "us.amazon.nova-2-lite-v1:0";

    BedrockGenMetadataConfig(String modelId, int timeoutSeconds, int maxAttempts) {
        super(Backend.BEDROCK, modelId, timeoutSeconds, maxAttempts);
    }

    public static BedrockGenMetadataConfig from(ConfigService configService) {
        String model = optionalModelId(configService);
        if (model == null) {
            model = DEFAULT_MODEL;
        }
        return new BedrockGenMetadataConfig(
            normalizeModelId(model),
            timeoutSeconds(configService),
            maxAttempts(configService));
    }

    /** Test helper when ConfigService is not involved. */
    public static BedrockGenMetadataConfig of(String modelId, int timeoutSeconds) {
        return new BedrockGenMetadataConfig(
            normalizeModelId(modelId), timeoutSeconds, DEFAULT_MAX_ATTEMPTS);
    }

    /**
     * Nova foundation-model ids ({@code amazon.nova-…}) cannot be invoked on-demand; Bedrock requires
     * a cross-region inference profile (e.g. {@code us.amazon.nova-2-lite-v1:0}). Bare Nova ids are
     * rewritten to the US profile. Ids that already have a geo/global prefix are left unchanged.
     */
    static String normalizeModelId(String modelId) {
        if (StringUtils.isBlank(modelId)) {
            return modelId;
        }
        String id = modelId.trim();
        if (id.startsWith("amazon.nova")) {
            return "us." + id;
        }
        return id;
    }
}
