package co.worklytics.psoxy;


import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import co.worklytics.psoxy.gateway.impl.*;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;

import co.worklytics.psoxy.gateway.output.OutputFactory;
import co.worklytics.psoxy.gcp.GcpKmsPublicKeyStoreClient;
import com.google.cloud.ServiceOptions;

import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import lombok.SneakyThrows;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.Optional;

/**
 * defines how to fulfill dependencies that need platform-specific implementations for GCP platform
 */
@Module(
    includes = {
        GcpModule.Bindings.class,
    }
)
public interface GcpModule {


    //NOTE: this is just convention; `-` is allowed in GCP Secret Manager Secret IDs
    static String asSecretManagerNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_") + "_";
    }
    @Provides
    @Singleton
    static GcpEnvironment gcpEnvironment() {
        return new GcpEnvironment();
    }

    // TODO: why can this be replaced with `@Binds @Singleton HostEnvironment hostEnvironment(GcpEnvironment gcpEnvironment)`?
    @Provides
    @Singleton
    static HostEnvironment hostEnvironment(GcpEnvironment gcpEnvironment) {
        return gcpEnvironment;
    }


    @Provides @Named("instance") @Singleton
    static SecretManagerConfigService instanceConfigService(HostEnvironment hostEnvironment,
                                               EnvVarsConfigService envVarsConfigService,
                                               SecretManagerConfigServiceFactory secretManagerConfigServiceFactory) {
        String pathToInstanceConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asSecretManagerNamespace(Optional.ofNullable(hostEnvironment.getInstanceId()).orElse("")));

        return secretManagerConfigServiceFactory.create(ServiceOptions.getDefaultProjectId(), pathToInstanceConfig);
    }

    @Provides @Singleton
    static LockService lockService(@Named("instance") SecretManagerConfigService instanceConfigService) {
        return instanceConfigService;
    }

    @Provides @Singleton
    static SecretStore secretStore(@Named("Native") SecretStore nativeSecretStore) {
        return nativeSecretStore;
    }

    @Provides @Singleton
    static Storage storage() {
        return StorageOptions.getDefaultInstance().getService();
    }

    /**
     * in GCP cloud function, we should be able to configure everything via env vars; either
     * directly or by binding them to secrets at function deployment:
     *
     * @see "https://cloud.google.com/functions/docs/configuring/env-var"
     * @see "https://cloud.google.com/functions/docs/configuring/secrets"
     *
     * but using env vars is problematic because it's bound at boot-time for two reasons:
     *  - even if reference 'latest' version of secret, it won't be updated until next boot
     *  - if an enabled, accessible version of the secret doesn't exist at boot-time, cloud function
     *    fails to boot (or even deploy from Terraform - it just times out)
     *
     */
    @Provides @Singleton @Named("Native")
    static SecretStore nativeSecretStore(EnvVarsConfigService envVarsConfigService,
                                         SecretManagerConfigServiceFactory secretManagerConfigServiceFactory,
                                         @Named("instance") SecretManagerConfigService instanceConfigService) {
        String pathToSharedConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                // Default is considered as empty; otherwise it will fail due a NPE
                .orElse("");

        SecretManagerConfigService shared = secretManagerConfigServiceFactory.create(ServiceOptions.getDefaultProjectId(), pathToSharedConfig);

        Duration proxyInstanceConfigCacheTtl = Duration.ofMinutes(5);
        Duration sharedConfigCacheTtl = Duration.ofMinutes(20);
        return CompositeConfigService.builder()
            .preferred(new CachingConfigServiceDecorator(instanceConfigService, proxyInstanceConfigCacheTtl))
            .fallback(new CachingConfigServiceDecorator(shared, sharedConfigCacheTtl))
            .build();
    }


    @Provides @Named("Native") @Singleton
    static ConfigService nativeConfigService(@Named("Native") SecretStore secretStore) {
        return secretStore;
    }

    @Provides
    @IntoSet
    static OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder providesSourceAuthStrategy(GCPWorkloadIdentityFederationGrantTokenRequestBuilder tokenRequestBuilder) {
        return tokenRequestBuilder;
    }

    @SneakyThrows
    @Provides
    static KeyManagementServiceClient providesKeyManagementServiceClient() {
        return KeyManagementServiceClient.create();
    }

    @Provides
    @Singleton
    static GcpEnvironment.WebhookCollectorModeConfig webhookCollectorModeConfig(ConfigService configService) {
        return GcpEnvironment.WebhookCollectorModeConfig.fromConfigService(configService);
    }

    @Module
    abstract class Bindings {

        @Binds
        abstract AsyncApiDataRequestHandler asyncApiDataRequestHandler(GcpAsyncApiDataRequestHandler asyncApiDataRequestHandler);

        @Binds
        @IntoSet
        abstract OutputFactory<?> outputFactory(GCSOutputFactory outputFactory);

        @Binds
        @IntoSet
        abstract OutputFactory<?> pubsubOutputFactory(PubSubOutputFactory pubSubOutputFactory);

        @Binds
        @IntoSet
        abstract PublicKeyStoreClient gcpKmsPublicKeyStoreClient(GcpKmsPublicKeyStoreClient impl);
    }
}
