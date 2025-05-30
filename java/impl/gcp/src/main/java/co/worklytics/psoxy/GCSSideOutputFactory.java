package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.SideOutputFactory;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;


@AssistedFactory
public interface GCSSideOutputFactory extends SideOutputFactory<GCSSideOutput> {

    GCSSideOutput create(@Assisted("bucket") String bucket,
                         @Assisted("pathPrefix") String pathPrefix);
}
