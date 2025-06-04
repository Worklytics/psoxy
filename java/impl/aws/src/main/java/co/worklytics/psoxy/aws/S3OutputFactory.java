package co.worklytics.psoxy.aws;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface S3OutputFactory {

    S3Output create(@Assisted S3Output.Options options);

}
