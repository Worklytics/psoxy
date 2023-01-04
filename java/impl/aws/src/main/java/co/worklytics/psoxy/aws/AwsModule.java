package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.*;
import co.worklytics.psoxy.gateway.impl.VaultConfigService;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
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
    static ParameterStoreConfigService parameterStoreConfigService(EnvVarsConfigService envVarsConfigService,
                                                                   SsmClient ssmClient) {

        String namespace = envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
            .orElse(null);

        return new ParameterStoreConfigService(namespace, ssmClient);
    }

    // parameters scoped to function
    // singleton to be reused in lambda container
    @Provides @Singleton
    static ParameterStoreConfigService functionParameterStoreConfigService(EnvVarsConfigService envVarsConfigService,
                                                                           SsmClient ssmClient) {
        String namespace =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asParameterStoreNamespace(System.getenv(RuntimeEnvironmentVariables.AWS_LAMBDA_FUNCTION_NAME.name())));
        return new ParameterStoreConfigService(namespace, ssmClient);
    }

    static String asParameterStoreNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_");
    }

    @Provides @Named("Native") @Singleton
    static ConfigService nativeConfigService(@Named("Global") ParameterStoreConfigService globalParameterStoreConfigService,
                                             ParameterStoreConfigService functionScopedParameterStoreConfigService) {

        Duration sharedTtl = Duration.ofMinutes(20);
        Duration connectorTtl = Duration.ofMinutes(5);
        return CompositeConfigService.builder()
            .preferred(new CachingConfigServiceDecorator(functionScopedParameterStoreConfigService, connectorTtl))
            .fallback(new CachingConfigServiceDecorator(globalParameterStoreConfigService, sharedTtl))
            .build();
    }

    @Provides
    static AmazonS3 getStorageClient() {
        return AmazonS3ClientBuilder.defaultClient();
    }

    @Provides @Singleton
    static Vault vault(EnvVarsConfigService envVarsConfigService,
                       VaultAwsIamAuthFactory vaultAwsIamAuthFactory) {
        if (envVarsConfigService.getConfigPropertyAsOptional(VaultConfigService.VaultConfigProperty.VAULT_TOKEN).isPresent()) {
            return VaultConfigService.createVaultClientFromEnvVarsToken(envVarsConfigService);
        } else {
            VaultAwsIamAuth vaultAwsIamAuth = vaultAwsIamAuthFactory.create(
                System.getenv(AwsModule.RuntimeEnvironmentVariables.AWS_REGION.name()),
                DefaultAWSCredentialsProviderChain.getInstance().getCredentials());
            VaultConfig vaultConfig = new VaultConfig()
                .sslConfig(new SslConfig())
                .address(envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_ADDR))
                .token(vaultAwsIamAuth.getToken());
            return new Vault(vaultConfig);
        }
    }
}
