package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.Optional;

/**
 * implementation of config service for tests
 *
 */
@RequiredArgsConstructor
public class MemoryConfigService implements ConfigService {

    final Map<String, String> map;

    @Override
    public boolean supportsWriting() {
        return true;
    }

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

    @Override
    public boolean isDevelopment() {
        return true;
    }
}
