package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.response.LogicalResponse;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.util.Map;
import java.util.Optional;

@Log
public class VaultConfigService implements ConfigService {

    //these config values must be provided as ENV_VARs to use vault as secret store
    public enum VaultConfigProperty implements ConfigProperty {
        VAULT_ADDR,
        VAULT_NAMESPACE, // optional; if omitted, won't be set
        VAULT_TOKEN, //makes this pretty f'ing circular ...
        ;
    }

    //q: vault caters to storing secrets in groups, as a "map" (eg key-value pairs)
    // do we want to follow this paradigm or not? this makes sense by scope (global, vs per-function
    // values), but that distinction not in Java implementation atm.
    public static final String VALUE_FIELD = "value";

    @Getter @NonNull
    final String path;

    Vault vault;

    @AssistedInject
    VaultConfigService(Vault vault, @Assisted @NonNull String path) {
        this.vault = vault;
        this.path = path;
    }

    private String path(@NonNull ConfigProperty configProperty) {
        return getPath() + configProperty.name();
    }

    @Override
    public boolean supportsWriting() {
        return true;
    }

    @SneakyThrows
    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        vault.logical()
            .write(path(property), Map.of(VALUE_FIELD, value));
    }

    @SneakyThrows
    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        LogicalResponse response = vault.logical()
            .read(path(property));
        if (response.getRestResponse().getStatus() == 200) {
            return response.getData().get(VALUE_FIELD);
        } else {
            //403 unless explicit ACL policy set
            log.info(property.name() + " " + response.getRestResponse().getStatus() + " " + new String(response.getRestResponse().getBody()));
            throw new IllegalStateException("Missing config value: " + property.name());
        }
    }

    @SneakyThrows
    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        LogicalResponse response = vault.logical()
            .read(path(property));


        if (response.getRestResponse().getStatus() == 200) {
            return Optional.ofNullable(response.getData().get(VALUE_FIELD));
        } else {
            //403 unless explicit ACL policy set
            log.info(property.name() + " " + response.getRestResponse().getStatus() + " " + new String(response.getRestResponse().getBody()));
            return Optional.empty();
        }
    }


}
