package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory SecretStore implementation for tests.
 */
@RequiredArgsConstructor
public class InMemorySecretStore implements SecretStore {

    final Map<String, String> map;

    @Override
    public void writeSecret(ConfigProperty property, String value) {
        map.put(property.name(), value);
    }

    @Override
    public String getConfigPropertyOrError(@NonNull ConfigProperty property) {
       return getConfigPropertyAsOptional(property)
           .orElseThrow(() ->  new Error("No property in config: " + property.name()));
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(@NonNull ConfigProperty property) {
        return Optional.ofNullable(map.get(property.name()));
    }

    @Override
    public List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit) {
        return Collections.emptyList();
    }
}
