package co.worklytics.psoxy;


import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.cloud.ServiceOptions;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;

/**
 * defines how to fulfill dependencies that need platform-specific implementations for GCP platform
 */
@Module
public interface GcpModule {

    // https://cloud.google.com/functions/docs/configuring/env-var#newer_runtimes
    enum RuntimeEnvironmentVariables {
        K_SERVICE
    }


    static String asParameterStoreNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_");
    }

    // global parameters
    // singleton to be reused in cloud function container
    @Provides
    @Named("Global")
    @Singleton
    static SecretManagerConfigService secretManagerConfigService() {
        // Global don't change that often, use longer TTL
        return new SecretManagerConfigService(null, ServiceOptions.getDefaultProjectId(), Duration.ofMinutes(20));
    }

    // parameters scoped to function
    // singleton to be reused in cloud function container
    @Provides
    @Singleton
    static SecretManagerConfigService functionSecretManagerConfigService() {
        String namespace =
                asParameterStoreNamespace(System.getenv(RuntimeEnvironmentVariables.K_SERVICE.name()));
        // Namespaced params may change often (refresh tokens), use shorter TTL
        return new SecretManagerConfigService(namespace, ServiceOptions.getDefaultProjectId(), Duration.ofMinutes(5));
    }

    /**
     * in GCP cloud function, we should be able to configure everything via env vars; either
     * directly or by binding them to secrets at function deployment:
     *
     * @see "https://cloud.google.com/functions/docs/configuring/env-var"
     * @see "https://cloud.google.com/functions/docs/configuring/secrets"
     */
    @Provides
    static ConfigService configService(EnvVarsConfigService envVarsConfigService,
                                       @Named("Global") SecretManagerConfigService globalSecretManagerConfigService,
                                       SecretManagerConfigService functionScopedSecretManagerConfigService) {

        CompositeConfigService parameterStoreConfigHierarchy = CompositeConfigService.builder()
                .fallback(globalSecretManagerConfigService)
                .preferred(functionScopedSecretManagerConfigService)
                .build();

        return CompositeConfigService.builder()
                .fallback(parameterStoreConfigHierarchy)
                .preferred(envVarsConfigService)
                .build();
    }
}