package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.impl.gen.BedrockGenMetadataConfig;
import co.worklytics.psoxy.impl.gen.GenMetadataChatModelProvider;
import co.worklytics.psoxy.impl.gen.GenMetadataConfig;
import dev.langchain4j.model.bedrock.BedrockChatModel;
import dev.langchain4j.model.bedrock.BedrockChatRequestParameters;
import dev.langchain4j.model.chat.ChatModel;

import lombok.NoArgsConstructor;

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
@NoArgsConstructor(onConstructor_ = @Inject)
public class BedrockChatModelProvider implements GenMetadataChatModelProvider {

    @Override
    public boolean supports(GenMetadataConfig config) {
        return config instanceof BedrockGenMetadataConfig;
    }

    @Override
    public ChatModel create(GenMetadataConfig config, Path modelCacheDir) {
        return BedrockChatModel.builder()
            .modelId(config.getModelId())
            .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
            .maxRetries(config.getMaxAttempts())
            .defaultRequestParameters(BedrockChatRequestParameters.builder()
                .temperature(0.0)
                .build())
            .build();
    }
}
