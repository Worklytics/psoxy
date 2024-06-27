package co.worklytics.psoxy;

import dagger.assisted.AssistedFactory;

@AssistedFactory
public interface CommandLineConfigServiceFactory {

    CommandLineConfigService create(String[] args);
}
