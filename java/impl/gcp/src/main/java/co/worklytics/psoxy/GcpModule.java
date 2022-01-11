package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public class GcpModule {

    @Provides
    @IntoSet
    SourceAuthStrategy providesSourceAuthStrategy(GoogleCloudPlatformServiceAccountKeyAuthStrategy googleCloudPlatformServiceAccountKeyAuthStrategy) {
        return googleCloudPlatformServiceAccountKeyAuthStrategy;
    }
}
