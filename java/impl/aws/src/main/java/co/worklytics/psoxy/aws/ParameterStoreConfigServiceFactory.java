package co.worklytics.psoxy.aws;

import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface ParameterStoreConfigServiceFactory {
    ParameterStoreConfigService create(String namespace);
}
