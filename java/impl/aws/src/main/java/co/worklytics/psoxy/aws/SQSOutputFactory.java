package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.output.OutputFactory;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface SQSOutputFactory extends OutputFactory<SQSOutput> {

    SQSOutput create(@Assisted OutputLocation location);

    default boolean supports(OutputLocation location) {
        return OutputLocation.LocationKind.SQS.equals(location.getKind());
    }
}
