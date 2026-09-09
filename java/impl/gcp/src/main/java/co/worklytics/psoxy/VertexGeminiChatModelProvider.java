package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.gen.GenMetadataChatModelProvider;
import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import com.google.cloud.MetadataConfig;
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
 * <p>Uses Application Default Credentials. Project and location match the Psoxy deployment:
 * project via {@link ServiceOptions#getDefaultProjectId()} / metadata; location via optional env
 * overrides, then the Cloud Run / Functions metadata server ({@code instance/region} or zone).
 */
@Log
@Singleton
public class VertexGeminiChatModelProvider implements GenMetadataChatModelProvider {

    /** Optional override; not injected by Cloud Functions gen2 / Cloud Run by default. */
    static final String ENV_GOOGLE_CLOUD_REGION = "GOOGLE_CLOUD_REGION";
    /** Gen1 Cloud Functions; may be absent on gen2. */
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
            "Vertex genMetadata requires a GCP project id (ADC / metadata / GOOGLE_CLOUD_PROJECT)");
    }

    String resolveLocation() {
        return Optional.ofNullable(firstNonBlank(
                System.getenv(ENV_GOOGLE_CLOUD_REGION),
                System.getenv(ENV_FUNCTION_REGION)))
            .orElseGet(() -> Optional.ofNullable(regionFromMetadata()).orElse(DEFAULT_LOCATION));
    }

    /**
     * Cloud Run / Functions gen2 do not set {@code FUNCTION_REGION}; read region (or zone) from
     * the GCE metadata server instead.
     */
    String regionFromMetadata() {
        try {
            String regionAttr = MetadataConfig.getAttribute("instance/region");
            String fromRegion = regionNameFromMetadataPath(regionAttr, "/regions/");
            if (fromRegion != null) {
                return fromRegion;
            }
            return regionFromZone(MetadataConfig.getZone());
        } catch (RuntimeException e) {
            log.fine("Could not read region from metadata server: " + e.getMessage());
            return null;
        }
    }

    static String regionNameFromMetadataPath(String path, String segment) {
        if (StringUtils.isBlank(path) || !path.contains(segment)) {
            return null;
        }
        return path.substring(path.lastIndexOf('/') + 1).trim();
    }

    /**
     * {@code projects/123/zones/us-central1-a} → {@code us-central1}
     */
    static String regionFromZone(String zonePath) {
        String zone = regionNameFromMetadataPath(zonePath, "/zones/");
        if (zone == null) {
            return null;
        }
        int lastDash = zone.lastIndexOf('-');
        return lastDash > 0 ? zone.substring(0, lastDash) : zone;
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
