package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.Output;
import co.worklytics.psoxy.gateway.ProcessedContent;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.extern.java.Log;
import software.amazon.awssdk.services.sqs.SqsClient;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collection;
import java.util.Collections;

@Log
public class SQSOutput implements Output {

    Options options;

    @AssistedInject
    public SQSOutput(@Assisted Options options) {
        this.options = options;
    }

    @With
    @Builder
    @Value
    public static class Options implements Output.Options {

        String queueUrl;
    }

    @Inject
    Provider<SqsClient> sqsClient;

    @Override
    public void write(ProcessedContent content) {
        batchWrite(Collections.singleton(content));
    }

    @Override
    public void batchWrite(Collection<ProcessedContent> contents) {
        try {
            SqsClient client = sqsClient.get();

            for (ProcessedContent content : contents) {
                // Send each message to the SQS queue
                client.sendMessage(builder -> builder
                    .queueUrl(options.getQueueUrl())
                    .messageBody(content.getContent()).build());
                //    .messageAttributes(sideOutputUtils.buildMetadata(content.getMetadata())));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to write batch of content to SQS", e);
        }

    }
}
