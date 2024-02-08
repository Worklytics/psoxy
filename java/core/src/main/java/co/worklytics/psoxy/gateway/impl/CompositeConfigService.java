package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.WritableConfigService;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * constructs a composite ConfigService built from two others
 *
 * checks 'preferred' first, and then fallback if not found there.
 *
 * although in practice we don't expect a given property to be defined in both, the value from
 * 'preferred' would be taken in such a scenario
 *
 */
@Builder
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CompositeConfigService implements ConfigService, SecretStore {

    //open to feedback on these names;
    @NonNull
    final ConfigService preferred;
    @NonNull
    final ConfigService fallback;


    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return preferred.getConfigPropertyAsOptional(property)
            .orElseGet(() ->
                fallback.getConfigPropertyAsOptional(property)
                    .orElseThrow(() -> new NoSuchElementException("Missing config. no value for " + property))
            );
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        return preferred.getConfigPropertyAsOptional(property).or(() -> fallback.getConfigPropertyAsOptional(property));
    }

    @Override
    public Optional<ConfigValueWithMetadata> getConfigPropertyWithMetadata(ConfigProperty configProperty) {
        return preferred.getConfigPropertyWithMetadata(configProperty)
            .or(() -> fallback.getConfigPropertyWithMetadata(configProperty));
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        if (preferred instanceof WritableConfigService) {
            ((WritableConfigService) preferred).putConfigProperty(property, value);
        } else {
            throw new UnsupportedOperationException("preferred ConfigService is not writable");
        }
    }
}
