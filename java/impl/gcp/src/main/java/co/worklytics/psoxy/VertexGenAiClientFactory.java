package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.impl.GcpHostedProcessIdentity;
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
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Builds google-genai {@link Client} instances for Vertex genMetadata (chat + cached content).
 *
 * <p>Project id comes from {@link ServiceOptions#getDefaultProjectId()} (same as other GCP module
 * wiring). Credentials use {@link GcpHostedProcessIdentity}.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class VertexGenAiClientFactory {

    public static final Duration PROMPT_CACHE_TTL = Duration.ofMinutes(55);

    /** Vertex cached-content display name; shared across Cloud Function instances for the same rule. */
    public static final String CACHE_DISPLAY_NAME_PREFIX = "psoxy-genmd-";

    private final GcpHostedProcessIdentity processIdentity;

    public String projectId() {
        String projectId = ServiceOptions.getDefaultProjectId();
        if (StringUtils.isBlank(projectId)) {
            throw new IllegalStateException(
                "Vertex genMetadata requires a GCP project id from Application Default Credentials");
        }
        return projectId;
    }

    public String cacheDisplayName(String staticPromptFingerprint) {
        return CACHE_DISPLAY_NAME_PREFIX + staticPromptFingerprint;
    }

    public VertexGenAiCachesContext cachesContext(String location) {
        Client client = vertexClient(location);
        GoogleGenAiCaches caches = GoogleGenAiCaches.builder().client(client).build();
        return new VertexGenAiCachesContext(caches, client);
    }

    Client vertexClient(String location) {
        Client.Builder builder = Client.builder()
            .vertexAI(true)
            .project(projectId())
            .location(location)
            .credentials(processIdentity.getCredentials());
        if ("global".equalsIgnoreCase(location)) {
            builder.httpOptions(HttpOptions.builder()
                .baseUrl("https://aiplatform.googleapis.com")
                .build());
        }
        return builder.build();
    }

    public Optional<CachedContent> findByDisplayName(GoogleGenAiCaches caches, String displayName) {
        for (CachedContent entry : caches.listCaches()) {
            if (entry.displayName().map(displayName::equals).orElse(false)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public CachedContent createCachedContent(Client client,
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

    @Value
    public static class VertexGenAiCachesContext {
        GoogleGenAiCaches caches;
        Client client;
    }
}
