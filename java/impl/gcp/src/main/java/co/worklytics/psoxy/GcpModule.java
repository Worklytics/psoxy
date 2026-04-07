package co.worklytics.psoxy;


import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Named;
import javax.inject.Singleton;
import com.google.cloud.ServiceOptions;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import co.worklytics.psoxy.gateway.AsyncApiDataRequestHandler;
import co.worklytics.psoxy.gateway.CompositeSecretStore;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import co.worklytics.psoxy.gateway.impl.CachingConfigServiceDecorator;
import co.worklytics.psoxy.gateway.impl.CachingSecretStoreDecorator;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.gateway.output.OutputFactory;
import co.worklytics.psoxy.gcp.GcpKmsPublicKeyStoreClient;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import lombok.SneakyThrows;

/**
 * defines how to fulfill dependencies that need platform-specific implementations for GCP platform
 */
@Module(
    includes = {
        GcpModule.Bindings.class,
    }
)
public interface GcpModule {

    java.util.logging.Logger log = java.util.logging.Logger.getLogger(GcpModule.class.getName());


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
    static SecretManagerSecretStore instanceConfigService(HostEnvironment hostEnvironment,
                                               EnvVarsConfigService envVarsConfigService,
                                               SecretManagerSecretStoreFactory secretManagerSecretStoreFactory) {
        // Prefer PATH_TO_INSTANCE_SECRETS if set; else fall back to PATH_TO_INSTANCE_CONFIG
        String pathToInstanceSecrets =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_SECRETS)
                .or(() -> envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG))
                .orElseGet(() -> asSecretManagerNamespace(Optional.ofNullable(hostEnvironment.getInstanceId()).orElse("")));

        return secretManagerSecretStoreFactory.create(ServiceOptions.getDefaultProjectId(), pathToInstanceSecrets);
    }

    @Provides @Singleton
    static LockService lockService(@Named("instance") SecretManagerSecretStore instanceConfigService) {
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
     * Secret store: backed by GCP Secret Manager.
     * Uses PATH_TO_INSTANCE_SECRETS / PATH_TO_SHARED_SECRETS if set;
     * falls back to PATH_TO_INSTANCE_CONFIG / PATH_TO_SHARED_CONFIG.
     */
    @Provides @Singleton @Named("Native")
    static SecretStore nativeSecretStore(EnvVarsConfigService envVarsConfigService,
                                         SecretManagerSecretStoreFactory secretManagerSecretStoreFactory,
                                         @Named("instance") SecretManagerSecretStore instanceConfigService) {
        // Prefer PATH_TO_SHARED_SECRETS if set; else fall back to PATH_TO_SHARED_CONFIG
        String pathToSharedSecrets =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_SECRETS)
                .or(() -> envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG))
                .orElse("");

        SecretManagerSecretStore shared = secretManagerSecretStoreFactory.create(ServiceOptions.getDefaultProjectId(), pathToSharedSecrets);

        Duration proxyInstanceConfigCacheTtl = Duration.ofMinutes(5);
        Duration sharedConfigCacheTtl = Duration.ofMinutes(20);
        return CompositeSecretStore.builder()
            .preferred(new CachingSecretStoreDecorator(instanceConfigService, proxyInstanceConfigCacheTtl))
            .fallback(new CachingSecretStoreDecorator(shared, sharedConfigCacheTtl))
            .build();
    }

    /**
     * Config service: backed by GCP Parameter Manager (for non-secret configuration).
     * Uses PATH_TO_INSTANCE_PARAMS / PATH_TO_SHARED_PARAMS if set;
     * falls back to PATH_TO_INSTANCE_CONFIG / PATH_TO_SHARED_CONFIG.
     * Environment-variable precedence is applied by higher-level composition outside this method.
     */
    @Provides @Named("Native") @Singleton
    static ConfigService nativeConfigService(EnvVarsConfigService envVarsConfigService,
                                             ParameterManagerConfigServiceFactory parameterManagerConfigServiceFactory,
                                             SecretManagerSecretStoreFactory secretManagerSecretStoreFactory) {
        String configStore = envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.CONFIG_STORE)
            .orElse("PARAMETER_MANAGER");

        String projectId = ServiceOptions.getDefaultProjectId();

        // Prefer PATH_TO_INSTANCE_PARAMS if set; else fall back to PATH_TO_INSTANCE_CONFIG
        String pathToInstanceParams =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_PARAMS)
                .or(() -> envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG))
                .orElse("");

        // Prefer PATH_TO_SHARED_PARAMS if set; else fall back to PATH_TO_SHARED_CONFIG
        String pathToSharedParams =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_PARAMS)
                .or(() -> envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG))
                .orElse("");

        if (pathToInstanceParams.isEmpty() && pathToSharedParams.isEmpty()) {
            log.warning("Neither PATH_TO_INSTANCE_PARAMS/PATH_TO_INSTANCE_CONFIG nor PATH_TO_SHARED_PARAMS/PATH_TO_SHARED_CONFIG is set; " +
                "Native config will not be available.");
            // return env vars only — no native backing
            return envVarsConfigService;
        }

        Duration instanceCacheTtl = Duration.ofMinutes(5);
        Duration sharedCacheTtl = Duration.ofMinutes(20);

        if ("SECRET_MANAGER".equals(configStore)) {
            SecretManagerSecretStore instanceSm = secretManagerSecretStoreFactory.create(projectId, pathToInstanceParams);
            SecretManagerSecretStore sharedSm = secretManagerSecretStoreFactory.create(projectId, pathToSharedParams);
            
            return CompositeConfigService.builder()
                .preferred(new CachingConfigServiceDecorator(instanceSm, instanceCacheTtl))
                .fallback(new CachingConfigServiceDecorator(sharedSm, sharedCacheTtl))
                .build();
        }

        ParameterManagerConfigService instancePm = parameterManagerConfigServiceFactory.create(projectId, pathToInstanceParams);
        ParameterManagerConfigService sharedPm = parameterManagerConfigServiceFactory.create(projectId, pathToSharedParams);

        return CompositeConfigService.builder()
            .preferred(new CachingConfigServiceDecorator(instancePm, instanceCacheTtl))
            .fallback(new CachingConfigServiceDecorator(sharedPm, sharedCacheTtl))
            .build();
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
    static GcpEnvironment.GcpWebhookCollectorModeConfig webhookCollectorModeConfig(ConfigService configService) {
        return GcpEnvironment.GcpWebhookCollectorModeConfig.fromConfigService(configService);
    }

    @Provides
    @Singleton
    static GcpEnvironment.ApiModeConfig apiModeConfig(ConfigService configService) {
        return GcpEnvironment.ApiModeConfig.fromConfigService(configService);
    }

    @Provides
    @Singleton
    static ExecutorService providesExecutorService() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown hook triggered");
            executorService.shutdown();
        }));

        return executorService;
    }

    @Provides @Singleton
    static AsyncApiDataRequestHandler apiDataRequestViaPubSub(ApiDataRequestViaPubSubFactory factory,
                                                              GcpEnvironment.ApiModeConfig config) {
        return factory.create(config.getAsyncPubSubQueue().orElseThrow(() -> new IllegalStateException("PubSub topic not configured")));
    }

    @Module
    abstract class Bindings {

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
