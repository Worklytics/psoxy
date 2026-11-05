package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.RequiredArgsConstructor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GenMetadataPromptCacheSupportFactory {

    private final Set<GenMetadataPromptCacheSupport> providers;
    private final NoOpGenMetadataPromptCacheSupport noOp;

    public GenMetadataPromptCacheSupport forConfig(GenMetadataConfig config) {
        if (config == null) {
            return noOp;
        }
        return providers.stream()
            .filter(p -> p.supports(config))
            .findFirst()
            .orElse(noOp);
    }
}
