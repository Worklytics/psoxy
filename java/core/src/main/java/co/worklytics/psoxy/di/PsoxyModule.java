package co.worklytics.psoxy.di;

import co.worklytics.psoxy.Sanitizer;
import co.worklytics.psoxy.impl.SanitizerImpl;
import dagger.Module;
import dagger.Provides;

@Module
public class PsoxyModule {

    @Provides
    Sanitizer providesSanitizer(SanitizerImpl sanitizer) {
        return sanitizer;
    }
}
