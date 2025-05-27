package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.SideOutputFactory;
import dagger.assisted.AssistedFactory;


@AssistedFactory
public interface S3SideOutputFactory extends SideOutputFactory<S3SideOutput> {

    S3SideOutput create(String bucket, String pathPrefix);
}
