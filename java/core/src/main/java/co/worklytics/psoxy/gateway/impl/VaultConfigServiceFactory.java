package co.worklytics.psoxy.gateway.impl;

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
}
