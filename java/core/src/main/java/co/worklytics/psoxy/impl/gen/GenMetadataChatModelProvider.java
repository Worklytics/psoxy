package co.worklytics.psoxy.impl.gen;

import dev.langchain4j.model.chat.ChatModel;

import java.nio.file.Path;

/**
 * Platform-pluggable LangChain4j {@link ChatModel} factory for genMetadata (Bedrock / Vertex).
 */
public interface GenMetadataChatModelProvider {

    boolean supports(GenMetadataConfig config);

    /**
     * @param modelCacheDir unused for cloud providers (retained for SPI stability)
     */
    ChatModel create(GenMetadataConfig config, Path modelCacheDir) throws Exception;
}
