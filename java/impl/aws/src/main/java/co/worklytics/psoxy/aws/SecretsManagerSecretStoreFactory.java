package co.worklytics.psoxy.aws;

import dagger.assisted.AssistedFactory;

@AssistedFactory
interface SecretsManagerSecretStoreFactory {

    SecretsManagerSecretStore create(String namespace);
}
