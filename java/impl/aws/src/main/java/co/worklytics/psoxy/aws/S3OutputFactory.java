package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.output.OutputFactory;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface S3OutputFactory extends OutputFactory<S3Output> {

    S3Output create(@Assisted OutputLocation options);

    default boolean supports(OutputLocation location) {
        return "sqs".equals(location.getKind());
    }
}
