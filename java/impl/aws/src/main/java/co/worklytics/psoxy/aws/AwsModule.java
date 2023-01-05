package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
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

import static co.worklytics.psoxy.aws.VaultAwsIamAuth.VAULT_ENGINE_VERSION;


/**
 * defines how to fulfill dependencies that need platform-specific implementations for GCP platform
 */
@Module
public interface AwsModule {

    @Provides @Singleton
    static AwsEnvironment awsEnvironment() {
        return new AwsEnvironment();
    }

    @Provides @Singleton
    static HostEnvironment hostEnvironment(AwsEnvironment awsEnvironment) {
        return awsEnvironment;
    }

    @Provides
    static SsmClient ssmClient(AwsEnvironment awsEnvironment) {
        Region region = Region.of(awsEnvironment.getRegion());
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
    static ParameterStoreConfigService functionParameterStoreConfigService(HostEnvironment hostEnvironment,
                                                                           EnvVarsConfigService envVarsConfigService,
                                                                           SsmClient ssmClient) {
        String namespace =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asParameterStoreNamespace(hostEnvironment.getInstanceId()));
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
    static Vault vault(AwsEnvironment awsEnvironment,
                       EnvVarsConfigService envVarsConfigService,
                       VaultAwsIamAuthFactory vaultAwsIamAuthFactory) {
        if (envVarsConfigService.getConfigPropertyAsOptional(VaultConfigService.VaultConfigProperty.VAULT_TOKEN).isPresent()) {
            return VaultConfigService.createVaultClientFromEnvVarsToken(envVarsConfigService);
        } else {
            VaultAwsIamAuth vaultAwsIamAuth = vaultAwsIamAuthFactory.create(
                awsEnvironment.getRegion(),
                DefaultAWSCredentialsProviderChain.getInstance().getCredentials());

            if (envVarsConfigService.isDevelopment()) {
                vaultAwsIamAuth.logCallerIdentity();
                vaultAwsIamAuth.preflightChecks(envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_ADDR));
            }

            VaultConfig vaultConfig = new VaultConfig()
                .engineVersion(VaultAwsIamAuth.VAULT_ENGINE_VERSION)
                .sslConfig(new SslConfig())
                .address(envVarsConfigService.getConfigPropertyOrError(VaultConfigService.VaultConfigProperty.VAULT_ADDR))
                .token(vaultAwsIamAuth.getToken());

            return new Vault(vaultConfig);
        }
    }
}
