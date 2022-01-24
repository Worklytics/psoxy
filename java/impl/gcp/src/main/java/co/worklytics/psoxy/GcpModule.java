package co.worklytics.psoxy;


import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.time.Clock;
import java.util.UUID;

/**
 * defines how to fulfill dependencies that need platform-specific implementations for GCP platform
 */
@Module
public interface GcpModule {

    /**
     * in GCP cloud function, we should be able to configure everything via env vars; either
     * directly or by binding them to secrets at function deployment:
     *
     * @see "https://cloud.google.com/functions/docs/configuring/env-var"
     * @see "https://cloud.google.com/functions/docs/configuring/secrets"
     */
    @Binds ConfigService configService(EnvVarsConfigService envVarsConfigService);

}
