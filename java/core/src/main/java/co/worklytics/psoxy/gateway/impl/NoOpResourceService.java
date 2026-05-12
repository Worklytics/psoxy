package co.worklytics.psoxy.gateway.impl;

import java.io.InputStream;
import java.util.Optional;

import co.worklytics.psoxy.gateway.ResourceService;

/**
 * No-op ResourceService that always returns empty. Used as a fallback when no resource
 * backend is configured.
 */
public class NoOpResourceService implements ResourceService {

    @Override
    public Optional<InputStream> getResource(String objectPath) {
        return Optional.empty();
    }

    @Override
    public boolean exists(String objectPath) {
        return false;
    }
}
