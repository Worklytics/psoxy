package co.worklytics.psoxy.aws;

import dagger.assisted.AssistedFactory;

@AssistedFactory
interface SecretsManagerConfigServiceFactory {

    SecretsManagerConfigService create(String namespace);
}
