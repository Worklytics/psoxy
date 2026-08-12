package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.impl.gen.GenMetadataChatModelProvider;
import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Amazon Bedrock ChatModel for genMetadata (AWS Lambda deployment only).
 *
 * <p>Uses the Lambda execution role / default AWS credentials chain; region from the default
 * AWS region provider (Lambda {@code AWS_REGION}).
 */
@Singleton
public class BedrockChatModelProvider implements GenMetadataChatModelProvider {

    @Inject
    public BedrockChatModelProvider() {
    }

    @Override
    public boolean supports(GenMetadataConfig config) {
        return GenMetadataConfig.BACKEND_BEDROCK.equalsIgnoreCase(config.getBackend());
    }

    @Override
    public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
        return BedrockChatModel.builder()
            .modelId(config.getModelId())
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .maxRetries(1)
            .defaultRequestParameters(BedrockChatRequestParameters.builder()
                .temperature(0.0)
                .maxOutputTokens(config.getMaxTokens())
                .build())
            .build();
    }
}
