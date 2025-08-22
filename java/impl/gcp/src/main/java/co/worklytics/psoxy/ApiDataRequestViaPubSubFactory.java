package co.worklytics.psoxy;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface ApiDataRequestViaPubSubFactory {


    /**
     * Creates an instance of {@link ApiDataRequestViaPubSub} with the specified topic name.
     *
     * @param topicName the name of the Pub/Sub topic to which messages will be published
     * @return a new instance of {@link ApiDataRequestViaPubSub}
     */
    ApiDataRequestViaPubSub create(@Assisted String topicName);
}
