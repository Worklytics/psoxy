package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.output.OutputFactory;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface S3OutputFactory extends OutputFactory<S3Output> {

    S3Output create(@Assisted OutputLocation location);

    @Override
    default boolean supports(OutputLocation location) {
        return OutputLocation.LocationKind.S3.equals(location.getKind());
    }
}
