package co.worklytics.psoxy.gateway;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.NoSuchElementException;
import java.util.Optional;

@Builder
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CompositeSecretStore implements SecretStore {    //open to feedback on these names;

    @NonNull
    final SecretStore preferred;

    @NonNull
    final SecretStore fallback;

    @Override
    public String getConfigPropertyOrError(ConfigService.ConfigProperty property) {
        return getConfigPropertyAsOptional(property)
            .orElseThrow(() -> new NoSuchElementException("Missing config. no value for " + property));
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigService.ConfigProperty property) {
        return preferred.getConfigPropertyAsOptional(property).or(() -> fallback.getConfigPropertyAsOptional(property));
    }

    @Override
    public Optional<ConfigService.ConfigValueWithMetadata> getConfigPropertyWithMetadata(ConfigService.ConfigProperty configProperty) {
        return preferred.getConfigPropertyWithMetadata(configProperty)
            .or(() -> fallback.getConfigPropertyWithMetadata(configProperty));
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        preferred.putConfigProperty(property, value);
    }
}
