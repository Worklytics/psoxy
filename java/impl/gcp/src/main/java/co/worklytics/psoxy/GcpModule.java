package co.worklytics.psoxy;


import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.BlindlyOptimisticLockService;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.VaultConfigService;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import com.bettercloud.vault.Vault;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.ServiceOptions;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;

/**
 * defines how to fulfill dependencies that need platform-specific implementations for GCP platform
 */
@Module
public interface GcpModule {


    //NOTE: this is just convention; `-` is allowed in GCP Secret Manager Secret IDs
    static String asSecretManagerNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_") + "_";
    }
    @Provides
    @Singleton
    static GcpEnvironment gcpEnvironment() {
        return new GcpEnvironment();
    }

    // TODO: why can this be replaced with `@Binds @Singleton HostEnvironment hostEnvironment(GcpEnvironment gcpEnvironment)`?
    @Provides
    @Singleton
    static HostEnvironment hostEnvironment(GcpEnvironment gcpEnvironment) {
        return gcpEnvironment;
    }


    @Provides @Named("instance") @Singleton
    static SecretManagerConfigService instanceConfigService(HostEnvironment hostEnvironment,
                                               EnvVarsConfigService envVarsConfigService,
                                               SecretManagerConfigServiceFactory secretManagerConfigServiceFactory) {
        String pathToInstanceConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asSecretManagerNamespace(hostEnvironment.getInstanceId()));

        return secretManagerConfigServiceFactory.create(ServiceOptions.getDefaultProjectId(), pathToInstanceConfig);
    }

    @Provides @Singleton
    static LockService lockService(@Named("instance") SecretManagerConfigService instanceConfigService) {
        return instanceConfigService;
    }

    /**
     * in GCP cloud function, we should be able to configure everything via env vars; either
     * directly or by binding them to secrets at function deployment:
     *
     * @see "https://cloud.google.com/functions/docs/configuring/env-var"
     * @see "https://cloud.google.com/functions/docs/configuring/secrets"
     */
    @Provides @Named("Native") @Singleton
    static ConfigService nativeConfigService(EnvVarsConfigService envVarsConfigService,
                                             SecretManagerConfigServiceFactory secretManagerConfigServiceFactory,
                                             @Named("instance") SecretManagerConfigService instanceConfigService) {
        String pathToSharedConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                .orElse(null);

        return CompositeConfigService.builder()
                .preferred(instanceConfigService)
                .fallback(secretManagerConfigServiceFactory.create(ServiceOptions.getDefaultProjectId(), pathToSharedConfig))
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

    @Provides
    @IntoSet
    static OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder providesSourceAuthStrategy(GCPWorkloadIdentityFederationGrantTokenRequestBuilder tokenRequestBuilder) {
        return tokenRequestBuilder;
    }
}
