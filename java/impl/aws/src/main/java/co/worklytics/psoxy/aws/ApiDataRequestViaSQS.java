package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.AsyncApiDataRequestHandler;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.NonNull;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.Map;

/**
 * An implementation of {@link AsyncApiDataRequestHandler} that handles API data requests via SQS.
 */
public class ApiDataRequestViaSQS implements AsyncApiDataRequestHandler {

    final String queueUrl;

    @Inject
    Provider<SqsClient> sqsClient;

    @Inject
    ObjectMapper objectMapper;

    @AssistedInject
    public ApiDataRequestViaSQS(@Assisted @NonNull String queueUrl) {
        this.queueUrl = queueUrl;
    }

    public static final String PROCESSING_CONTEXT_ATTRIBUTE = "processingContext";

    @Override
    public void handle(HttpEventRequest request, ApiDataRequestHandler.ProcessingContext processingContext) {
        try {
            SqsClient client = sqsClient.get();
            SendMessageRequest.Builder requestBuilder = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageAttributes(Map.of(PROCESSING_CONTEXT_ATTRIBUTE, MessageAttributeValue.builder()
                        .dataType("String")
                    .stringValue(objectMapper.writeValueAsString(processingContext))
                        .build()))
                .messageBody(objectMapper.writeValueAsString(request));

            client.sendMessage(requestBuilder.build());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send message to SQS queue: " + queueUrl, e);
        }
    }
}
