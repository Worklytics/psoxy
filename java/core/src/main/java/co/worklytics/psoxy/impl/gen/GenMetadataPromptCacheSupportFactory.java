package co.worklytics.psoxy.impl.gen;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

@Singleton
public class GenMetadataPromptCacheSupportFactory {

    private final Set<GenMetadataPromptCacheSupport> providers;
    private final NoOpGenMetadataPromptCacheSupport noOp;

    @Inject
    public GenMetadataPromptCacheSupportFactory(Set<GenMetadataPromptCacheSupport> providers,
                                                NoOpGenMetadataPromptCacheSupport noOp) {
        this.providers = providers;
        this.noOp = noOp;
    }

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
