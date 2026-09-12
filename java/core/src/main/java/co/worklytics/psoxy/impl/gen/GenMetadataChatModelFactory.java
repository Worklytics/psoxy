package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.util.Set;

/**
 * Dispatches to a {@link GenMetadataChatModelProvider} that supports the configured backend
 * (Bedrock on AWS, Vertex on GCP).
 */
@Log
@Singleton
public class GenMetadataChatModelFactory {

    private final Set<GenMetadataChatModelProvider> providers;

    @Inject
    public GenMetadataChatModelFactory(Set<GenMetadataChatModelProvider> providers) {
        this.providers = providers;
    }

    public boolean supports(GenMetadataConfig config) {
        return config != null
            && config.isSupportedCloudBackend()
            && providers.stream().anyMatch(p -> p.supports(config));
    }

    public ChatModel create(GenMetadataConfig config, Path modelCacheDir) throws Exception {
        return providers.stream()
            .filter(p -> p.supports(config))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "genMetadata backend '" + config.getBackend()
                    + "' is not available in this deployment bundle"))
            .create(config, modelCacheDir);
    }
}
