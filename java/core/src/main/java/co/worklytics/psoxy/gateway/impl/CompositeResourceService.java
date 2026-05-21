package co.worklytics.psoxy.gateway.impl;

import java.io.InputStream;
import java.util.Optional;

import co.worklytics.psoxy.gateway.ResourceService;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Composite ResourceService that checks {@code preferred} first, falling back to {@code fallback}.
 *
 * <p>Analogous to {@link CompositeConfigService} — provides failover semantics for resource
 * resolution.</p>
 */
@Builder
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CompositeResourceService implements ResourceService {

    @NonNull
    final ResourceService preferred;

    @NonNull
    final ResourceService fallback;

    @Override
    public Optional<InputStream> getResource(String objectPath) {
        Optional<InputStream> result = preferred.getResource(objectPath);
        if (result.isPresent()) {
            return result;
        }
        return fallback.getResource(objectPath);
    }
}
