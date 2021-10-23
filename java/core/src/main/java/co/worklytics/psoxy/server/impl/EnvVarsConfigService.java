package co.worklytics.psoxy.server.impl;

import co.worklytics.psoxy.server.ConfigService;

import java.util.Optional;

public class EnvVarsConfigService implements ConfigService {

    public String getConfigPropertyOrError(ConfigProperty property) {
        String value = System.getenv(property.name());
        if (value == null) {
            throw new Error("Psoxy misconfigured. Expected value for: " + property.name());
        }
        return value;
    }

    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        return Optional.ofNullable(System.getenv(property.name()));
    }
}
