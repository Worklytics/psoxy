package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariables({
    @EnabledIfEnvironmentVariable(named = "VAULT_ADDR", matches = ".*"),
    @EnabledIfEnvironmentVariable(named = "VAULT_TOKEN", matches = ".*")
})
class VaultConfigServiceIntegrationTest {

    VaultConfigService vaultConfigService;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        //in Intellij, set env vars in Run/Debug Configurations
        String vaultAddress = System.getenv("VAULT_ADDR");
        String vaultToken = System.getenv("VAULT_TOKEN");
        Optional<String> vaultNamespace = Optional.ofNullable(System.getenv("VAULT_NAMESPACE"));


        VaultConfig config = new VaultConfig()
            .address(vaultAddress)
            .token(vaultToken);

        vaultNamespace.ifPresent(s -> {
            try {
                config.nameSpace(s);
            } catch (VaultException e) {
                throw new RuntimeException(e);
            }
        });

        Vault vault = new Vault(config.build());
        vaultConfigService = new VaultConfigService(vault, "secret/");
    }

    @Test
    void getConfigPropertyOrError() {

        assertEquals("salt",
            vaultConfigService.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT));

    }
}
