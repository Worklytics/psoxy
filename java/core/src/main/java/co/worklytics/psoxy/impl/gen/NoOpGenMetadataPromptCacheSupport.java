package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.model.chat.request.ChatRequest;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NoOpGenMetadataPromptCacheSupport implements GenMetadataPromptCacheSupport {

    @Inject
    public NoOpGenMetadataPromptCacheSupport() {
    }

    @Override
    public boolean supports(GenMetadataConfig config) {
        return false;
    }

    @Override
    public void configureRequest(GenMetadataConfig config,
                                 GenMetadataPromptAssembly assembly,
                                 ChatRequest.Builder requestBuilder) {
        requestBuilder.messages(assembly.getUncachedMessages());
    }
}
