package co.worklytics.psoxy;


import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.*;
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
import java.time.Duration;

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

    @Provides @Singleton
    static SecretStore secretStore(@Named("Native") SecretStore nativeSecretStore,
                                   HostEnvironment hostEnvironment,
                                   VaultConfigServiceFactory vaultConfigServiceFactory,
                                   EnvVarsConfigService envVarsConfigService) {
        if (vaultConfigServiceFactory.isVaultConfigured(envVarsConfigService)) {
            if (!vaultConfigServiceFactory.isVaultConfigured(envVarsConfigService)) {
                throw new IllegalStateException("Vault is not configured, but HASHICORP_VAULT secret store implementation was requested");
            }
            VaultConfigService  sharedVault =
                vaultConfigServiceFactory.createInitialized(vaultConfigServiceFactory.pathForSharedVault(hostEnvironment, envVarsConfigService));
            VaultConfigService instanceVault =
                vaultConfigServiceFactory.createInitialized(vaultConfigServiceFactory.pathForInstanceVault(hostEnvironment, envVarsConfigService));

            return CompositeSecretStore.builder()
                .preferred(instanceVault)
                .fallback(sharedVault)
                .build();
        } else {
            return nativeSecretStore;
        }
    }

    /**
     * in GCP cloud function, we should be able to configure everything via env vars; either
     * directly or by binding them to secrets at function deployment:
     *
     * @see "https://cloud.google.com/functions/docs/configuring/env-var"
     * @see "https://cloud.google.com/functions/docs/configuring/secrets"
     *
     * but using env vars is problematic because it's bound at boot-time for two reasons:
     *  - even if reference 'latest' version of secret, it won't be updated until next boot
     *  - if an enabled, accessible version of the secret doesn't exist at boot-time, cloud function
     *    fails to boot (or even deploy from Terraform - it just times out)
     *
     */
    @Provides @Singleton @Named("Native")
    static SecretStore nativeSecretStore(EnvVarsConfigService envVarsConfigService,
                                         SecretManagerConfigServiceFactory secretManagerConfigServiceFactory,
                                         @Named("instance") SecretManagerConfigService instanceConfigService) {
        String pathToSharedConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                // Default is considered as empty; otherwise it will fail due a NPE
                .orElse("");

        SecretManagerConfigService shared = secretManagerConfigServiceFactory.create(ServiceOptions.getDefaultProjectId(), pathToSharedConfig);

        Duration proxyInstanceConfigCacheTtl = Duration.ofMinutes(5);
        Duration sharedConfigCacheTtl = Duration.ofMinutes(20);
        return CompositeConfigService.builder()
            .preferred(new CachingConfigServiceDecorator(instanceConfigService, proxyInstanceConfigCacheTtl))
            .fallback(new CachingConfigServiceDecorator(shared, sharedConfigCacheTtl))
            .build();
    }


    @Provides @Named("Native") @Singleton
    static ConfigService nativeConfigService(@Named("Native") SecretStore secretStore) {
        return secretStore;
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
