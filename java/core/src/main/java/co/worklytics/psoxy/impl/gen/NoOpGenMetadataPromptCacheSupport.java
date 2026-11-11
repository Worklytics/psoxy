package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.model.chat.request.ChatRequest;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@NoArgsConstructor(onConstructor_ = @Inject)
public class NoOpGenMetadataPromptCacheSupport implements GenMetadataPromptCacheSupport {

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
