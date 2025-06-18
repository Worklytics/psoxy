package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.extern.java.Log;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Log
public class SQSOutput implements Output {

    final String queueUrl;

    public static class MessageAttributes {
        public static final String CONTENT_TYPE = "Content-Type";
        public static final String CONTENT_ENCODING = "Content-Encoding";
    }

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
                .messageAttributes(Map.of(
                    MessageAttributes.CONTENT_TYPE,  MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(Optional.ofNullable(content.getContentType()).orElse("application/octet-stream"))
                            .build(),
                    MessageAttributes.CONTENT_ENCODING, software.amazon.awssdk.services.sqs.model.MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue(Optional.ofNullable(content.getContentEncoding()).orElse("identity"))
                            .build()
                    )
                )
                .messageBody(content.getContentAsString());  // contentAsString totally F'd up if it's gzipped? Should we base64 encode it? ;

            client.sendMessage(requestBuilder.build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to write batch of content to SQS", e);
        }

    }
}
