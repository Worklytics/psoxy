package co.worklytics.psoxy.impl.gen;

import co.worklytics.psoxy.gateway.ConfigService;
import com.avaulta.gateway.rules.augments.GenMetadataThinkingLevels;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

/**
 * Vertex AI Gemini deployment config. Region and thinking-level env keys live here (GCP/Vertex
 * only), not on {@link GenMetadataConfig.ConfigProperty}.
 *
 * <p>The LangChain4j ChatModel adapter is GCP-module {@code VertexGeminiChatModelProvider}.
 */
@Getter
public final class VertexGenMetadataConfig extends GenMetadataConfig {

    /** Default when {@link ConfigProperty#GEN_METADATA_MODEL} is unset. */
    public static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

    /**
     * Default Vertex publisher-model location. The google-genai client maps {@code global} to
     * {@code https://aiplatform.googleapis.com}; multi-region {@code us}/{@code eu} use regional
     * publisher hosts.
     */
    public static final String DEFAULT_MODEL_REGION = "global";

    public enum ConfigProperty implements ConfigService.ConfigProperty {
        /**
         * Vertex AI location for the model endpoint (default {@code global}). Independent of
         * where the Cloud Function runs.
         */
        GEN_METADATA_MODEL_REGION,
        /**
         * Gemini thinking level ({@code minimal}|{@code low}|{@code medium}|{@code high}).
         * Default {@code minimal}.
         */
        GEN_METADATA_THINKING_LEVEL,
        ;
    }

    private final String modelRegion;
    private final String thinkingLevel;

    VertexGenMetadataConfig(String modelId, int timeoutSeconds, int maxAttempts,
                            String modelRegion, String thinkingLevel) {
        super(Backend.VERTEX, modelId, timeoutSeconds, maxAttempts);
        this.modelRegion = modelRegion;
        this.thinkingLevel = thinkingLevel;
    }

    public static VertexGenMetadataConfig from(ConfigService configService) {
        String model = optionalModelId(configService);
        if (model == null) {
            model = DEFAULT_MODEL;
        }
        String modelRegion = configService.getConfigPropertyAsOptional(ConfigProperty.GEN_METADATA_MODEL_REGION)
            .filter(StringUtils::isNotBlank)
            .map(String::trim)
            .orElse(DEFAULT_MODEL_REGION);
        String thinkingLevel = configService.getConfigPropertyAsOptional(ConfigProperty.GEN_METADATA_THINKING_LEVEL)
            .filter(StringUtils::isNotBlank)
            .map(GenMetadataThinkingLevels::resolve)
            .orElse(GenMetadataThinkingLevels.DEFAULT);
        return new VertexGenMetadataConfig(
            model,
            timeoutSeconds(configService),
            maxAttempts(configService),
            modelRegion,
            thinkingLevel);
    }

    /** Test helper when ConfigService is not involved. */
    public static VertexGenMetadataConfig of(String modelId, String modelRegion, int timeoutSeconds) {
        String region = StringUtils.isNotBlank(modelRegion) ? modelRegion.trim() : DEFAULT_MODEL_REGION;
        return new VertexGenMetadataConfig(
            modelId, timeoutSeconds, DEFAULT_MAX_ATTEMPTS, region, GenMetadataThinkingLevels.DEFAULT);
    }

    public static VertexGenMetadataConfig of(String modelId, String modelRegion, String thinkingLevel,
                                             int timeoutSeconds) {
        String region = StringUtils.isNotBlank(modelRegion) ? modelRegion.trim() : DEFAULT_MODEL_REGION;
        return new VertexGenMetadataConfig(
            modelId, timeoutSeconds, DEFAULT_MAX_ATTEMPTS, region,
            GenMetadataThinkingLevels.resolve(thinkingLevel));
    }
}
