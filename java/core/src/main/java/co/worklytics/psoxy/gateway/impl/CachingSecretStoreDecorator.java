package co.worklytics.psoxy.gateway.impl;

import java.time.Duration;
import java.util.List;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import lombok.NonNull;


/**
 * Caching decorator for SecretStore.
 *
 * Extends the read-only {@link CachingConfigServiceDecorator} with secret write support via
 * {@link SecretStore#writeSecret}. The delegate MUST be a {@link SecretStore}.
 */
public class CachingSecretStoreDecorator extends CachingConfigServiceDecorator implements SecretStore {

    private final SecretStore secretStoreDelegate;

    public CachingSecretStoreDecorator(@NonNull SecretStore delegate, Duration defaultTtl) {
        super(delegate, defaultTtl);
        this.secretStoreDelegate = delegate;
    }

    @Override
    public void writeSecret(ConfigProperty property, String value) {
        if (!property.noCache()) {
            getCache().put(property, value);
        }
        secretStoreDelegate.writeSecret(property, value);
    }

    @Override
    public List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit) {
        // Don't cache version lists, always delegate
        return secretStoreDelegate.getAvailableVersions(property, limit);
    }

}
