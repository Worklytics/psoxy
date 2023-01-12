package co.worklytics.psoxy;


import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
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


    static String asSecretManagerNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_");
    }
    @Provides
    @Singleton
    static GcpEnvironment gcpEnvironment() {
        return new GcpEnvironment();
    }
    @Provides
    @Singleton
    static HostEnvironment hostEnvironment(GcpEnvironment gcpEnvironment) {
        return gcpEnvironment;
    }

    /**
     * in GCP cloud function, we should be able to configure everything via env vars; either
     * directly or by binding them to secrets at function deployment:
     *
     * @see "https://cloud.google.com/functions/docs/configuring/env-var"
     * @see "https://cloud.google.com/functions/docs/configuring/secrets"
     */
    @Provides @Named("Native") @Singleton
    static ConfigService nativeConfigService(HostEnvironment hostEnvironment,
                                             EnvVarsConfigService envVarsConfigService) {
        String pathToSharedConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                .orElse(null);

        String pathToInstanceConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asSecretManagerNamespace(hostEnvironment.getInstanceId()) + "_");

        return CompositeConfigService.builder()
                .preferred(new SecretManagerConfigService(pathToInstanceConfig, ServiceOptions.getDefaultProjectId()))
                .fallback(new SecretManagerConfigService(pathToSharedConfig, ServiceOptions.getDefaultProjectId()))
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
