package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataChatModelProvider;
import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import co.worklytics.psoxy.impl.gen.VertexGenMetadataConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Vertex AI Gemini ChatModel for genMetadata (GCP Cloud Function deployment only).
 *
 * <p>Uses the google-genai LangChain4j adapter (Application Default Credentials). Project via
 * {@link VertexGenAiClientFactory#projectId()}. Model location and thinking level come
 * from {@link VertexGenMetadataConfig} (not the Cloud Function region).
 *
 * <p>Gemini 3.5 Flash defaults to {@code MEDIUM} thinking, which shares {@code maxOutputTokens}
 * and often blows the per-call timeout for tiny classify JSON. Prefer {@code thinking_level}
 * over legacy {@code thinking_budget} (they must not be set together).
 *
 * <p>Per-call {@code maxOutputTokens} comes from the augment {@code maxOutputTokens} field (default
 * 200). Gemini thinking shares that per-call budget with visible JSON.
 *
 * <p>For {@code location=global}, google-genai uses {@code https://aiplatform.googleapis.com}
 * (not {@code global-aiplatform.googleapis.com}).
 */
@Log
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VertexGeminiChatModelProvider implements GenMetadataChatModelProvider {

    private final VertexGenAiClientFactory clientFactory;

    @Override
    public boolean supports(GenMetadataConfig config) {
        return config instanceof VertexGenMetadataConfig;
    }

    @Override
    public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
        if (!(config instanceof VertexGenMetadataConfig vertex)) {
            throw new IllegalStateException(
                "VertexGeminiChatModelProvider requires VertexGenMetadataConfig, got "
                    + config.getClass().getSimpleName());
        }
        String project = clientFactory.projectId();
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
}
