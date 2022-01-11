package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.SanitizerImpl;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface SanitizerFactory {

    SanitizerImpl create(Sanitizer.Options options);

}
