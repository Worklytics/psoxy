package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.output.OutputFactory;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;


@AssistedFactory
public interface PubSubOutputFactory extends OutputFactory<PubSubOutput> {

    @Override
    PubSubOutput create(@Assisted OutputLocation location);

    @Override
    default boolean supports(OutputLocation outputLocation) {
        return outputLocation.getUri() != null
        && outputLocation.getUri().startsWith(OutputLocation.LocationKind.PUBSUB.getUriPrefix());
    }
}

