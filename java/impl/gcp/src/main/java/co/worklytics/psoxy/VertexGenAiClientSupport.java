package co.worklytics.psoxy;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ServiceOptions;
import com.google.genai.Client;
import com.google.genai.types.CachedContent;
import com.google.genai.types.Content;
import com.google.genai.types.CreateCachedContentConfig;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.Part;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.google.genai.GoogleGenAiCaches;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shared Vertex {@link Client} construction for genMetadata (chat + cached content).
 */
final class VertexGenAiClientSupport {

    static final Duration PROMPT_CACHE_TTL = Duration.ofMinutes(55);

    /** Vertex cached-content display name; shared across Cloud Function instances for the same rule. */
    static final String CACHE_DISPLAY_NAME_PREFIX = "psoxy-genmd-";

    private VertexGenAiClientSupport() {
    }

    static String cacheDisplayName(String staticPromptFingerprint) {
        return CACHE_DISPLAY_NAME_PREFIX + staticPromptFingerprint;
    }

    static String resolveProjectId() {
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

    static Client vertexClient(String projectId, String location) {
        try {
            Client.Builder builder = Client.builder()
                .vertexAI(true)
                .project(projectId)
                .location(location)
                .credentials(GoogleCredentials.getApplicationDefault());
            if ("global".equalsIgnoreCase(location)) {
                builder.httpOptions(HttpOptions.builder()
                    .baseUrl("https://aiplatform.googleapis.com")
                    .build());
            }
            return builder.build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load Application Default Credentials", e);
        }
    }

    static VertexGenAiCachesContext cachesContext(String projectId, String location) {
        Client client = vertexClient(projectId, location);
        GoogleGenAiCaches caches = GoogleGenAiCaches.builder().client(client).build();
        return new VertexGenAiCachesContext(caches, client);
    }

    static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    record VertexGenAiCachesContext(GoogleGenAiCaches caches, Client client) {
    }

    static Optional<CachedContent> findByDisplayName(GoogleGenAiCaches caches, String displayName) {
        for (CachedContent entry : caches.listCaches()) {
            if (entry.displayName().map(displayName::equals).orElse(false)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    static CachedContent createCachedContent(Client client,
                                             String modelId,
                                             List<ChatMessage> prefixMessages,
                                             String displayName,
                                             Duration ttl) {
        CreateCachedContentConfig.Builder configBuilder = CreateCachedContentConfig.builder()
            .ttl(ttl)
            .displayName(displayName);
        Content systemInstruction = null;
        List<Content> contents = new ArrayList<>();
        for (ChatMessage message : prefixMessages) {
            if (message instanceof SystemMessage systemMessage) {
                systemInstruction = Content.builder()
                    .role("system")
                    .parts(List.of(Part.builder().text(systemMessage.text()).build()))
                    .build();
            } else if (message instanceof UserMessage userMessage) {
                contents.add(Content.builder()
                    .role("user")
                    .parts(List.of(Part.builder().text(userMessage.singleText()).build()))
                    .build());
            } else {
                throw new IllegalArgumentException(
                    "Unsupported cached prefix message: " + message.getClass().getSimpleName());
            }
        }
        if (!contents.isEmpty()) {
            configBuilder.contents(contents);
        }
        if (systemInstruction != null) {
            configBuilder.systemInstruction(systemInstruction);
        }
        return client.caches.create(modelId, configBuilder.build());
    }
}
