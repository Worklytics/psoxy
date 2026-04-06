package co.worklytics.psoxy.gateway;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

public interface SecretStore extends WritableConfigService {

    /**
     * Get available versions of a secret, ordered by version DESC (most recent first).
     *
     * This allows retrieving previous versions of secrets, which can be useful for cases like
     * OAuth token rotation where a recently-rotated token might not yet be fully propagated.
     *
     * @param property the secret property to retrieve versions for
     * @param limit maximum number of versions to return
     * @return list of available versions, ordered by version DESC; empty list if not supported or no versions exist
     */
    List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit);


    // --- New secret-specific API ---

    /**
     * Read a secret value, returning empty if not found.
     *
     * @param property identifying the secret
     * @return the secret value, or empty
     */
    default Optional<String> getSecret(ConfigProperty property) {
        return getConfigPropertyAsOptional(property);
    }

    /**
     * Read a secret value with metadata (e.g., last modified date).
     *
     * @param property identifying the secret
     * @return the secret value with metadata, or empty
     */
    default Optional<ConfigService.ConfigValueWithMetadata> getSecretWithMetadata(ConfigProperty property) {
        return getConfigPropertyWithMetadata(property);
    }

    /**
     * Read a secret value, throwing if not found.
     *
     * @param property identifying the secret
     * @return the secret value
     * @throws NoSuchElementException if the secret is not found
     */
    default String getSecretOrError(ConfigProperty property) {
        return getSecret(property)
                .orElseThrow(() -> new NoSuchElementException("Missing secret: no value for " + property));
    }

    /**
     * Write a secret value.
     *
     * @param property identifying the secret
     * @param value the secret value to write
     */
    default void writeSecret(ConfigProperty property, String value) {
        putConfigProperty(property, value);
    }

    /**
     * Write a secret value with retries.
     *
     * @param property identifying the secret
     * @param value the secret value to write
     * @param retries number of times to retry on failure
     * @throws WritePropertyRetriesExhaustedException if write fails after designated retries
     */
    default void writeSecret(ConfigProperty property, String value, int retries) throws WritePropertyRetriesExhaustedException {
        putConfigProperty(property, value, retries);
    }

}
