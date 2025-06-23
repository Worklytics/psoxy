package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.output.OutputFactory;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface GCSOutputFactory extends OutputFactory<GCSOutput> {


    GCSOutput create(@Assisted OutputLocation outputLocation);

    @Override
    default boolean supports(OutputLocation outputLocation) {
        return OutputLocation.LocationKind.GCS.equals(outputLocation.getKind());
    }
}
