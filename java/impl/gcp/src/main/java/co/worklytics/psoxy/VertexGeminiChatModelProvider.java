package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataChatModelProvider;
import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import co.worklytics.psoxy.impl.gen.VertexGenMetadataConfig;
import com.google.cloud.ServiceOptions;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Vertex AI Gemini ChatModel for genMetadata (GCP Cloud Function deployment only).
 *
 * <p>Uses the google-genai LangChain4j adapter (Application Default Credentials). Project via
 * {@link ServiceOptions#getDefaultProjectId()} / metadata. Model location and thinking level come
 * from {@link VertexGenMetadataConfig} (not the Cloud Function region).
 *
 * <p>Gemini 3.5 Flash defaults to {@code MEDIUM} thinking, which shares {@code maxOutputTokens}
 * and often blows the per-call timeout for tiny classify JSON. Prefer {@code thinking_level}
 * over legacy {@code thinking_budget} (they must not be set together).
 *
 * <p>Per-call {@code maxOutputTokens} comes from the augment {@code maxTokens} field (default
 * 200). Gemini thinking shares that per-call budget with visible JSON.
 *
 * <p>For {@code location=global}, google-genai uses {@code https://aiplatform.googleapis.com}
 * (not {@code global-aiplatform.googleapis.com}).
 */
@Log
@Singleton
public class VertexGeminiChatModelProvider implements GenMetadataChatModelProvider {

    @Inject
    public VertexGeminiChatModelProvider() {
    }

    @Override
    public boolean supports(GenMetadataConfig config) {
        return config instanceof VertexGenMetadataConfig;
    }

    @Override
    public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
        VertexGenMetadataConfig vertex = (VertexGenMetadataConfig) config;
        String project = resolveProjectId();
        log.info("Creating Vertex Gemini chat model project=" + project
            + " location=" + vertex.getModelRegion()
            + " model=" + vertex.getModelId()
            + " thinkingLevel=" + vertex.getThinkingLevel());
        return GoogleGenAiChatModel.builder()
            .projectId(project)
            .location(vertex.getModelRegion())
            .modelName(vertex.getModelId())
            .thinkingLevel(vertex.getThinkingLevel())
            .timeout(Duration.ofSeconds(vertex.getTimeoutSeconds()))
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

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
