package co.worklytics.psoxy.gateway.impl;

import java.io.InputStream;
import java.util.Optional;

import com.avaulta.gateway.resources.ResourceService;

/**
 * No-op ResourceService that always returns empty. Used as a fallback when no resource
 * backend is configured.
 */
public class NoOpResourceService implements ResourceService {

    @Override
    public Optional<InputStream> getResource(String objectPath) {
        ResourceService.validatePath(objectPath);
        return Optional.empty();
    }
}
