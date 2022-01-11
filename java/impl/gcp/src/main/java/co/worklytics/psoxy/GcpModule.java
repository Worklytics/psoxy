package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import dagger.Provides;
import dagger.multibindings.IntoSet;

public class GcpModule {

    @Provides
    @IntoSet
    SourceAuthStrategy providesSourceAuthStrategy(GoogleCloudPlatformServiceAccountKeyAuthStrategy googleCloudPlatformServiceAccountKeyAuthStrategy) {
        return googleCloudPlatformServiceAccountKeyAuthStrategy;
    }
}
