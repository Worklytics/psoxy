package co.worklytics.psoxy;


import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.*;
import com.bettercloud.vault.Vault;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ServiceOptions;
import dagger.Module;
import dagger.Provides;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;

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
        return new SecretManagerConfigService(null, ServiceOptions.getDefaultProjectId());
    }

    // parameters scoped to function
    // singleton to be reused in cloud function container
    @Provides
    @Singleton
    static SecretManagerConfigService functionSecretManagerConfigService() {
        String namespace =
                asParameterStoreNamespace(System.getenv(RuntimeEnvironmentVariables.K_SERVICE.name()));
        return new SecretManagerConfigService(namespace, ServiceOptions.getDefaultProjectId());
    }

    /**
     * in GCP cloud function, we should be able to configure everything via env vars; either
     * directly or by binding them to secrets at function deployment:
     *
     * @see "https://cloud.google.com/functions/docs/configuring/env-var"
     * @see "https://cloud.google.com/functions/docs/configuring/secrets"
     */
    @Provides @Named("Native") @Singleton
    static ConfigService nativeConfigService(@Named("Global") SecretManagerConfigService globalSecretManagerConfigService,
                                            SecretManagerConfigService functionScopedSecretManagerConfigService) {

        return CompositeConfigService.builder()
                .fallback(globalSecretManagerConfigService)
                .preferred(functionScopedSecretManagerConfigService)
                .build();

    }

    @Provides @Singleton
    static Vault vault(EnvVarsConfigService envVarsConfigService,
                       VaultGcpIamAuth vaultGcpIamAuth) {
        if (envVarsConfigService.getConfigPropertyAsOptional(VaultConfigService.VaultConfigProperty.VAULT_TOKEN).isPresent()) {
            return VaultConfigService.createVaultClientFromEnvVarsToken(envVarsConfigService);
        } else {
            String vaultAddr =
                envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_ADDR);

            try {
                GoogleCredentials googleCredentials = GoogleCredentials.getApplicationDefault();
                return vaultGcpIamAuth.createVaultClient(vaultAddr, CloudFunctionRequest.getFunctionName(), googleCredentials);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }
}
