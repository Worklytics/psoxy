package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.*;
import co.worklytics.psoxy.gateway.impl.VaultConfigService;
import co.worklytics.psoxy.gateway.impl.VaultConfigServiceFactory;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;


/**
 * defines how to fulfill dependencies that need platform-specific implementations for GCP platform
 */
@Module
public interface AwsModule {

    /**
     * see "https://docs.aws.amazon.com/lambda/latest/dg/configuration-envvars.html#configuration-envvars-runtimer"
     */
    enum RuntimeEnvironmentVariables {
        AWS_REGION,
        AWS_LAMBDA_FUNCTION_NAME,
    }

    @Provides
    static SsmClient ssmClient() {
        Region region = Region.of(System.getenv(RuntimeEnvironmentVariables.AWS_REGION.name()));
        return SsmClient.builder()
            // Add custom retry policy
            .overrideConfiguration(ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder()
                    .numRetries(4)
                    .throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy())
                    .build())
                .build())
            .region(region)
            .build();
    }

    // global parameters
    // singleton to be reused in lambda container
    @Provides @Named("Global") @Singleton
    static ParameterStoreConfigService parameterStoreConfigService(SsmClient ssmClient) {

        return new ParameterStoreConfigService(null, ssmClient);
    }

    // parameters scoped to function
    // singleton to be reused in lambda container
    @Provides @Singleton
    static ParameterStoreConfigService functionParameterStoreConfigService(SsmClient ssmClient) {
        String namespace =
            asParameterStoreNamespace(System.getenv(RuntimeEnvironmentVariables.AWS_LAMBDA_FUNCTION_NAME.name()));
        return new ParameterStoreConfigService(namespace, ssmClient);
    }

    static String asParameterStoreNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_");
    }

    @Provides
    static ConfigService configService(EnvVarsConfigService envVarsConfigService,
                                       @Named("Global") ParameterStoreConfigService globalParameterStoreConfigService,
                                       ParameterStoreConfigService functionScopedParameterStoreConfigService,
                                       VaultConfigServiceFactory vaultConfigServiceFactory
                                      ) {

        Duration sharedTtl = Duration.ofMinutes(20);
        Duration connectorTtl = Duration.ofMinutes(5);
        CompositeConfigService parameterStoreConfigHierarchy = CompositeConfigService.builder()
            .preferred(new CachingConfigServiceDecorator(functionScopedParameterStoreConfigService, connectorTtl))
            .fallback(new CachingConfigServiceDecorator(globalParameterStoreConfigService, sharedTtl))
            .build();

        //TODO: use a factory/provider for ParameterStoreConfigService, and get path-relative instances
        // in the same way? or add path to ConfigService interface?
        String sharedPath
            = envVarsConfigService
                .getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                .orElse("");
        String connectorPath
            = envVarsConfigService
                .getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_CONNECTOR_CONFIG)
                .orElse("");

        ConfigService remoteConfigService;


        if (vaultConfigServiceFactory.isVaultConfigured(envVarsConfigService)) {

            VaultConfigService sharedVault = vaultConfigServiceFactory.create(sharedPath);
            VaultConfigService connectorVault = vaultConfigServiceFactory.create(connectorPath);

            CompositeConfigService vaultConfigService = CompositeConfigService.builder()
                .preferred(new CachingConfigServiceDecorator(connectorVault, connectorTtl))
                .fallback(new CachingConfigServiceDecorator(sharedVault, sharedTtl))
                .build();

            remoteConfigService = CompositeConfigService.builder()
                .preferred(vaultConfigService)
                //fallback to parameter store, if not defined in Vault
                .fallback(parameterStoreConfigHierarchy)
                .build();
        } else {
            remoteConfigService = parameterStoreConfigHierarchy;
        }

        return CompositeConfigService.builder()
            .preferred(envVarsConfigService) //prefer env vars over remote
            .fallback(remoteConfigService)
            .build();
    }

    @Provides
    static AmazonS3 getStorageClient() {
        return AmazonS3ClientBuilder.defaultClient();
    }
}
