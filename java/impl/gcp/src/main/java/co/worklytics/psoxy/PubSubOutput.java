package co.worklytics.psoxy;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.ReceivedMessage;
import com.google.pubsub.v1.TopicName;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;

@Log
public class PubSubOutput implements Output {

    /**
     * The name of the PubSub topic to which messages will be published.
     * * This is expected to be in the format `projects/{project}/topics/{topic}`.
     * @see TopicName::parse(String)
     */
    final String topicName;

    @AssistedInject
    public PubSubOutput(@Assisted OutputLocation location) {
        // expects location.getUri() to be a PubSub topic URL
        this.topicName = location.getUri().replace("https://pubsub.googleapis.com/", "");
    }

    static enum MessageAttributes {
        CONTENT_TYPE("Content-Type"),
        CONTENT_ENCODING("Content-Encoding"),
        ;
        
        @Getter
        private final String stringEncoding;
        
        MessageAttributes(String stringEncoding) {
            this.stringEncoding = stringEncoding;
        }


        /**
         * parse value for attribute from ReceivedMessage
         * @param message to parse attribute value from
         * @return value of attribute, if present, otherwise empty
         */
        Optional<String> getValue(@NonNull ReceivedMessage message) {
            return Optional.ofNullable(message.getMessage().getAttributesMap().get(stringEncoding));
        }

    }


    @Override
    public void write(String key, ProcessedContent content) throws WriteFailure {
        try {
            Publisher publisher = Publisher.newBuilder(TopicName.parse(topicName)).build();
            ByteString data = ByteString.copyFrom(content.getContent());
            PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder().setData(data);
            if (content.getContentType() != null) {
                messageBuilder.putAttributes(MessageAttributes.CONTENT_TYPE.getStringEncoding(), content.getContentType());
            }
            if (content.getContentEncoding() != null) {
                messageBuilder.putAttributes(MessageAttributes.CONTENT_ENCODING.getStringEncoding(), content.getContentEncoding());
            }
            publisher.publish(messageBuilder.build()).get();
        } catch (InterruptedException | ExecutionException | IOException e) {
            log.log(Level.WARNING, "Failed to publish to PubSub", e);
            throw new WriteFailure("Failed to publish to PubSub", e);
        }
    }

    @Override
    public void write(ProcessedContent content) throws WriteFailure {
        write(null, content);
    }

}

