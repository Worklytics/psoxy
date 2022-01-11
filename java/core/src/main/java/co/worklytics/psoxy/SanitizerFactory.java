package co.worklytics.psoxy;

import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface SanitizerFactory {

    Sanitizer create(Sanitizer.Options options);

}
