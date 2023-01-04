package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import java.util.Optional;


@NoArgsConstructor(onConstructor_ = @Inject)
public class EnvVarsConfigService implements ConfigService {

    @Override
    public boolean supportsWriting() {
        return false;
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        throw new UnsupportedOperationException();
    }

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
