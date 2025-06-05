package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.extern.java.Log;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collection;
import java.util.Collections;

@Log
public class SQSOutput implements Output {

    final String queueUrl;


    @AssistedInject
    public SQSOutput(@Assisted OutputLocation location) {
        this.queueUrl = location.getUri();
    }

    @Inject
    Provider<SqsClient> sqsClient;

    @Override
    public void write(ProcessedContent content) {
        write(null, content);
    }

    @Override
    public void write(@Nullable String key, ProcessedContent content) {
        try {
            SqsClient client = sqsClient.get();

            SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(content.getContentAsString());

            //q : make use of `key`??

            // Send each message to the SQS queue
            client.sendMessage(builder -> builder
                .queueUrl(queueUrl)
                .messageBody(content.getContentAsString()).build());
            //    .messageAttributes(sideOutputUtils.buildMetadata(content.getMetadata())));

        } catch (Exception e) {
            throw new RuntimeException("Failed to write batch of content to SQS", e);
        }

    }
}
