package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ConnectivityFailureMatcher;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import dagger.Lazy;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.AllArgsConstructor;

/**
 * Detects required source-auth configuration properties that are absent from both the
 * environment/config service and the secret store.
 */
@AllArgsConstructor(onConstructor_ = @Inject)
public class MissingConfigChecker {

    ConfigService config;
    SecretStore secretStore;
    Lazy<SourceAuthStrategy> sourceAuthStrategy;
    ConnectivityFailureMatcher connectivityFailures;

    /**
     * @return names of required auth-related config properties that are not configured
     */
    public Set<String> findMissingRequiredConfigProperties() {
        try {
            return sourceAuthStrategy.get().getRequiredConfigProperties().stream()
                    .filter(configProperty -> config.getConfigPropertyAsOptional(configProperty)
                            .filter(value -> !value.isBlank()).isEmpty())
                    .filter(configProperty -> secretStore.getConfigPropertyAsOptional(configProperty)
                            .filter(value -> !value.isBlank()).isEmpty())
                    .map(ConfigService.ConfigProperty::name)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (Throwable e) {
            if (connectivityFailures.match(e).isPresent()) {
                if (e instanceof Error error) {
                    throw error;
                }
                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw new IllegalStateException(e);
            }
            // source auth strategy may not be wired in some edge cases; match health check behavior
            return Set.of();
        }
    }
}
