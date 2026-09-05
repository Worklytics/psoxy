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
import java.util.Optional;

/**
 * Vertex AI Gemini ChatModel for genMetadata (GCP Cloud Function deployment only).
 *
 * <p>Uses Application Default Credentials. Project and location match the Psoxy deployment
 * ({@link ServiceOptions#getDefaultProjectId()} and the function region from the environment).
 */
@Log
@Singleton
public class VertexGeminiChatModelProvider implements GenMetadataChatModelProvider {

    /** Injected by Cloud Run / Functions gen2 when set by Terraform from {@code gcp_region}. */
    static final String ENV_GOOGLE_CLOUD_REGION = "GOOGLE_CLOUD_REGION";
    static final String ENV_FUNCTION_REGION = "FUNCTION_REGION";
    static final String DEFAULT_LOCATION = "us-central1";

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
        String location = resolveLocation();
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
            "Vertex genMetadata requires a GCP project id (ADC / GOOGLE_CLOUD_PROJECT)");
    }

    String resolveLocation() {
        return Optional.ofNullable(firstNonBlank(
                System.getenv(ENV_GOOGLE_CLOUD_REGION),
                System.getenv(ENV_FUNCTION_REGION)))
            .orElse(DEFAULT_LOCATION);
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
