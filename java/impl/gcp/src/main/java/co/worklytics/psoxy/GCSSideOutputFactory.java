package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.output.OutputLocation;
import co.worklytics.psoxy.gateway.output.SideOutputFactory;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;


@AssistedFactory
public interface GCSSideOutputFactory extends SideOutputFactory<GCSSideOutput> {

    GCSSideOutput create(@Assisted OutputLocation location);
}
