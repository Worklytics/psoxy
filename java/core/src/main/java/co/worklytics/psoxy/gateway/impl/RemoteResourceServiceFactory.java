package co.worklytics.psoxy.gateway.impl;

import java.util.function.BiFunction;

import co.worklytics.psoxy.gateway.RemoteResourceConfig;
import co.worklytics.psoxy.gateway.ResourceService;

/**
 * Factory helpers for constructing platform-specific {@link ResourceService} instances
 * from {@link RemoteResourceConfig}.
 */
public final class RemoteResourceServiceFactory {

    private RemoteResourceServiceFactory() {
    }

    /**
     * Build a ResourceService scoped to the shared resource path prefix, or a no-op if
     * remote resources are not configured.
     */
    public static ResourceService sharedRemote(RemoteResourceConfig config,
                                               BiFunction<String, String, ResourceService> creator) {
        if (config.getBucket().isEmpty() || config.getSharedResourcePath().isEmpty()) {
            return new NoOpResourceService();
        }
        return creator.apply(config.getBucket().get(), config.getSharedResourcePath().get());
    }
}
