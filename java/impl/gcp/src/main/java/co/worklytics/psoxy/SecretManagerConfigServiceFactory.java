package co.worklytics.psoxy;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface SecretManagerConfigServiceFactory {


    SecretManagerConfigService create(@Assisted("projectId") String projectId,
                                      @Assisted("namespace") String namespace);
}
