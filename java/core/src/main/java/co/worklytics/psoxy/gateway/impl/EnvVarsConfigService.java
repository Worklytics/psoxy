package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import java.util.NoSuchElementException;
import java.util.Optional;


@NoArgsConstructor(onConstructor_ = @Inject)
public class EnvVarsConfigService implements ConfigService {

    public String getConfigPropertyOrError(ConfigProperty property) {
        String value = System.getenv(property.name());
        if (value == null) {
            throw new NoSuchElementException("Psoxy misconfigured. Expected value for: " + property.name());
        }
        return value;
    }

    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        return Optional.ofNullable(System.getenv(property.name()));
    }
}
