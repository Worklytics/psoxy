package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataChatModelProvider;
import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import com.google.cloud.ServiceOptions;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.vertexai.gemini.VertexAiGeminiChatModel;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;

/**
 * Vertex AI Gemini ChatModel for genMetadata (GCP Cloud Function deployment only).
 *
 * <p>Uses Application Default Credentials. Project via {@link ServiceOptions#getDefaultProjectId()}
 * / metadata. Model location comes from {@link GenMetadataConfig#getModelRegion()}
 * ({@code METADATA_GEN_MODEL_REGION}, default {@code global}) — independent of the Cloud Function
 * region, because publisher models like {@code gemini-3.5-flash} are served from global / multi-region
 * endpoints rather than every single region.
 */
@Log
@Singleton
public class VertexGeminiChatModelProvider implements GenMetadataChatModelProvider {

    @Inject
    public VertexGeminiChatModelProvider() {
    }

    @Override
    public boolean supports(GenMetadataConfig config) {
        return GenMetadataConfig.BACKEND_VERTEX.equalsIgnoreCase(config.getBackend());
    }

    @Override
    public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
        String project = resolveProjectId();
        String location = resolveModelLocation(config);
        log.info("Creating Vertex Gemini chat model project=" + project
            + " location=" + location + " model=" + config.getModelId());
        return VertexAiGeminiChatModel.builder()
            .project(project)
            .location(location)
            .modelName(config.getModelId())
            .temperature(0f)
            .maxOutputTokens(config.getMaxTokens())
            .maxRetries(1)
            .build();
    }

    String resolveProjectId() {
        String fromAdc = ServiceOptions.getDefaultProjectId();
        if (StringUtils.isNotBlank(fromAdc)) {
            return fromAdc;
        }
        String fromEnv = firstNonBlank(
            System.getenv("GOOGLE_CLOUD_PROJECT"),
            System.getenv("GCP_PROJECT"),
            System.getenv("GCLOUD_PROJECT"));
        if (fromEnv != null) {
            return fromEnv;
        }
        throw new IllegalStateException(
            "Vertex genMetadata requires a GCP project id (ADC / metadata / GOOGLE_CLOUD_PROJECT)");
    }

    /**
     * Vertex publisher-model location from config ({@code METADATA_GEN_MODEL_REGION}), not the
     * function's deployment region.
     */
    String resolveModelLocation(GenMetadataConfig config) {
        if (config != null && StringUtils.isNotBlank(config.getModelRegion())) {
            return config.getModelRegion().trim();
        }
        return GenMetadataConfig.DEFAULT_VERTEX_MODEL_REGION;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (StringUtils.isNotBlank(v)) {
                return v.trim();
            }
        }
        return null;
    }
}
