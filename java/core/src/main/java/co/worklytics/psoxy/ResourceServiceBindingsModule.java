package co.worklytics.psoxy;

import javax.inject.Named;
import javax.inject.Singleton;

import com.avaulta.gateway.resources.ResourceService;
import com.avaulta.gateway.rules.augments.SentenceMetadataProcessor;

import co.worklytics.psoxy.gateway.impl.CompositeResourceService;
import co.worklytics.psoxy.gateway.impl.LocalFileResourceService;
import dagger.Module;
import dagger.Provides;

/**
 * Shared {@link ResourceService} composition for augment runtimes (local FS → instance remote → shared remote).
 *
 * <p>Platform modules must provide {@code @Named("Remote")} and {@code @Named("SharedRemote")}.</p>
 */
@Module
public class ResourceServiceBindingsModule {

    @Provides
    @Singleton
    static ResourceService instanceResourceService(@Named("Remote") ResourceService remoteResourceService) {
        return CompositeResourceService.builder()
            .preferred(new LocalFileResourceService(ResourceService.DEFAULT_LOCAL_RESOURCE_PATH))
            .fallback(remoteResourceService)
            .build();
    }

    @Provides
    @Singleton
    @Named("ForGenMetadata")
    static ResourceService genMetadataResourceService(@Named("Remote") ResourceService remoteResourceService,
                                                      @Named("SharedRemote") ResourceService sharedRemoteResourceService) {
        ResourceService instanceResourceService = CompositeResourceService.builder()
            .preferred(new LocalFileResourceService(ResourceService.DEFAULT_LOCAL_RESOURCE_PATH))
            .fallback(remoteResourceService)
            .build();
        return CompositeResourceService.builder()
            .preferred(instanceResourceService)
            .fallback(sharedRemoteResourceService)
            .build();
    }

    @Provides
    @Singleton
    @Named("OpenNlp")
    static ResourceService openNlpResourceService(ResourceService instanceResourceService,
                                                  @Named("SharedRemote") ResourceService sharedRemoteResourceService) {
        return CompositeResourceService.builder()
            .preferred(instanceResourceService)
            .fallback(sharedRemoteResourceService)
            .build();
    }

    @Provides
    @Singleton
    static SentenceMetadataProcessor sentenceMetadataProcessor(@Named("OpenNlp") ResourceService openNlpResourceService) {
        return new SentenceMetadataProcessor(openNlpResourceService);
    }
}
