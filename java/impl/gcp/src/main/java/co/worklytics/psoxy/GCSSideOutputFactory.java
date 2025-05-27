package co.worklytics.psoxy;

import dagger.assisted.AssistedFactory;


@AssistedFactory
public interface GCSSideOutputFactory {

    GCSSideOutput create(String bucket);
}
