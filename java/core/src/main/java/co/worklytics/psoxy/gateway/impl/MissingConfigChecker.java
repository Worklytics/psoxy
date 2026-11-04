package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import dagger.Lazy;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.NoArgsConstructor;

/**
 * Detects required source-auth configuration properties that are absent from both the
 * environment/config service and the secret store.
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class MissingConfigChecker {

    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;
    @Inject
    Lazy<SourceAuthStrategy> sourceAuthStrategy;

    /**
     * @return names of required auth-related config properties that are not configured
     */
    public Set<String> findMissingRequiredConfigProperties() {
        try {
            return sourceAuthStrategy.get().getRequiredConfigProperties().stream()
                    .filter(configProperty -> config.getConfigPropertyAsOptional(configProperty).isEmpty())
                    .filter(configProperty -> secretStore.getConfigPropertyAsOptional(configProperty).isEmpty())
                    .map(ConfigService.ConfigProperty::name)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (Throwable e) {
            // source auth strategy may not be wired in some edge cases; match health check behavior
            return Set.of();
        }
    }
}
