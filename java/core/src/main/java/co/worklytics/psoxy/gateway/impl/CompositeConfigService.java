package co.worklytics.psoxy.gateway.impl;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.WritableConfigService;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

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

    @Override
    public List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit) {
        List<ConfigService.ConfigValueVersion> versions;
        // Try preferred first if it's a SecretStore, otherwise fall back
        if (preferred instanceof SecretStore) {
            versions = ((SecretStore) preferred).getAvailableVersions(property, limit);
        } else {
            versions = preferred.getConfigPropertyWithMetadata(property).map(value -> ConfigService.ConfigValueVersion.builder()
                .value(value.getValue())
                .lastModifiedDate(value.getLastModifiedDate().orElse(null))
                .version(null)
                .build()).stream().collect(Collectors.toList());
        }

        if (!versions.isEmpty()) {
            return versions;
        }

        // Try fallback if it's a SecretStore
        if (fallback instanceof SecretStore) {
            versions = ((SecretStore) fallback).getAvailableVersions(property, limit);
        } else {
            versions = fallback.getConfigPropertyWithMetadata(property).map(value -> ConfigService.ConfigValueVersion.builder()
                .value(value.getValue())
                .lastModifiedDate(value.getLastModifiedDate().orElse(null))
                .version(null)
                .build()).stream().collect(Collectors.toList());
        }

        return versions;
    }
}
