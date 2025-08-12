package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.AsyncApiDataRequestHandler;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import co.worklytics.psoxy.gateway.output.Output;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

@Log
public class ApiDataRequestViaPubSub implements AsyncApiDataRequestHandler {

    /**
     * The name of the PubSub topic to which messages will be published.
     * * This is expected to be in the format `projects/{project}/topics/{topic}`.
     * @see TopicName ::parse(String)
     */
    final String topicName;

    @Inject
    ObjectMapper objectMapper;

    @AssistedInject
    public ApiDataRequestViaPubSub(@Assisted @NonNull String topicName) {
        this.topicName = topicName;
    }

    @AllArgsConstructor
    enum MessageAttributes {
        PROCESSING_CONTEXT("processingContext"),
        ;

        @Getter
        private final String stringEncoding;
    }


    @Override
    public void handle(HttpEventRequest requestToProxy, ApiDataRequestHandler.ProcessingContext processingContext) {
        try {
            Publisher publisher = Publisher.newBuilder(TopicName.parse(topicName)).build();
            ByteString data = ByteString.copyFrom(objectMapper.writeValueAsBytes(requestToProxy.getUnderlyingRepresentation()));
            PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder()
                .putAttributes(MessageAttributes.PROCESSING_CONTEXT.getStringEncoding(), objectMapper.writeValueAsString(processingContext))
                .setData(data);
            String messageId = publisher.publish(messageBuilder.build()).get();
            log.log(Level.INFO, "Published message with ID: {0} to PubSub topic: {1}", new Object[]{messageId, topicName});
        } catch (InterruptedException | ExecutionException | IOException e) {
            throw new RuntimeException("Failed to publish message to PubSub topic: " + topicName, e);
        }
    }
}
