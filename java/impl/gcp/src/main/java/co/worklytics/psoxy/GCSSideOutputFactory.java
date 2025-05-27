package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.SideOutputFactory;
import dagger.assisted.AssistedFactory;


@AssistedFactory
public interface GCSSideOutputFactory extends SideOutputFactory<GCSSideOutput> {

    GCSSideOutput create(String bucket);
}
