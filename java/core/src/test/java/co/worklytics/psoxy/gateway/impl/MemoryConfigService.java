package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.WritableConfigService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Optional;

/**
 * implementation of config service for tests
 *
 */
@RequiredArgsConstructor
public class MemoryConfigService implements WritableConfigService {

    final Map<String, String> map;

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
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
}
