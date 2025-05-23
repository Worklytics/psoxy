package co.worklytics.psoxy.aws;

import dagger.assisted.AssistedFactory;


@AssistedFactory
public interface S3SideOutputFactory {

    S3SideOutput create(String bucket);
}
