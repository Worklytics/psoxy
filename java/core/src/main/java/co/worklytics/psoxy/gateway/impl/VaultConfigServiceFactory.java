package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import dagger.assisted.AssistedFactory;

//see https://dagger.dev/dev-guide/assisted-injection.html
@AssistedFactory
public interface VaultConfigServiceFactory {

    VaultConfigService create(String path);

    default boolean isVaultConfigured(EnvVarsConfigService envVarsConfigService) {
        //kinda, weird; purpose of this is to avoid exposing knowledge of required configuration
        // outside of vault's package
        return envVarsConfigService
            .getConfigPropertyAsOptional(VaultConfigService.VaultConfigProperty.VAULT_ADDR)
            .isPresent();
    }

    static String pathForSharedVault(HostEnvironment hostEnvironment, EnvVarsConfigService envVarsConfigService) {
        return envVarsConfigService
            .getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
            .orElse("secret/PSOXY_GLOBAL/");
    }

    static String pathForInstanceVault(HostEnvironment hostEnvironment, EnvVarsConfigService envVarsConfigService) {
        String instanceIdAsPathFragment = hostEnvironment.getInstanceId().toUpperCase().replace("-", "_");
        return envVarsConfigService
            .getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
            .orElse("secret/PSOXY_LOCAL/" + instanceIdAsPathFragment + "/");
    }
}
