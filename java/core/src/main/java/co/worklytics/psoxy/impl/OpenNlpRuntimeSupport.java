package co.worklytics.psoxy.impl;

import com.avaulta.gateway.resources.BinaryResourceProvider;
import com.avaulta.gateway.rules.augments.SentenceMetadataProcessor;

import co.worklytics.psoxy.gateway.ResourceService;

/**
 * Installs runtime OpenNLP model loading via {@link ResourceService}.
 */
public final class OpenNlpRuntimeSupport {

    private OpenNlpRuntimeSupport() {
    }

    /**
     * Configure {@link SentenceMetadataProcessor} to resolve models from resource services.
     */
    public static void install(ResourceService instanceResourceService,
                               ResourceService sharedRemoteResourceService) {
        BinaryResourceProvider provider =
            new OpenNlpModelResourceProvider(instanceResourceService, sharedRemoteResourceService);
        SentenceMetadataProcessor.configureResourceProvider(provider);
    }
}
