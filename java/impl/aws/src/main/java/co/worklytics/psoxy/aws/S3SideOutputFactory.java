package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.output.SideOutputFactory;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;


@AssistedFactory
public interface S3SideOutputFactory extends SideOutputFactory<S3SideOutput> {

    S3SideOutput create(@Assisted("bucket") String bucket, @Assisted("pathPrefix") String pathPrefix);
}
