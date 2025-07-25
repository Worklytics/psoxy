package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

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

    @Override
    public void write(String key, ProcessedContent content) throws WriteFailure {
        try {
            Publisher publisher = Publisher.newBuilder(TopicName.parse(topicName)).build();
            ByteString data = ByteString.copyFrom(content.getContent());
            PubsubMessage.Builder messageBuilder = PubsubMessage.newBuilder().setData(data);
            if (key != null) {
                messageBuilder.putAttributes("key", key);
            }
            if (content.getContentType() != null) {
                messageBuilder.putAttributes("contentType", content.getContentType());
            }
            if (content.getContentEncoding() != null) {
                messageBuilder.putAttributes("contentEncoding", content.getContentEncoding());
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

