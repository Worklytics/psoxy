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
import com.bettercloud.vault.VaultException;
import dagger.Module;
import dagger.Provides;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import javax.inject.Named;
import javax.inject.Provider;
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

    static String asParameterStoreNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_");
    }

    @Provides @Named("Native") @Singleton
    static ConfigService nativeConfigService(HostEnvironment hostEnvironment,
                                             ParameterStoreConfigServiceFactory parameterStoreConfigServiceFactory,
                                             EnvVarsConfigService envVarsConfigService) {

        ParameterStoreConfigService sharedParameterStoreConfigService =
                parameterStoreConfigServiceFactory.create(envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                    .orElse(null));

        ParameterStoreConfigService functionScopedParameterStoreConfigService =
            parameterStoreConfigServiceFactory.create(envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asParameterStoreNamespace(hostEnvironment.getInstanceId()) + "_"));

        Duration sharedTtl = Duration.ofMinutes(20);
        Duration connectorTtl = Duration.ofMinutes(5);
        return CompositeConfigService.builder()
            .preferred(new CachingConfigServiceDecorator(functionScopedParameterStoreConfigService, connectorTtl))
            .fallback(new CachingConfigServiceDecorator(sharedParameterStoreConfigService, sharedTtl))
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

            envVarsConfigService.getConfigPropertyAsOptional(VaultConfigService.VaultConfigProperty.VAULT_NAMESPACE)
                .filter(StringUtils::isNotBlank)  //don't bother tossing error here, assume meant no namespace
                .ifPresent(ns -> {
                    try {
                        vaultConfig.nameSpace(ns);
                    } catch (VaultException e) {
                        throw new Error("Error setting Vault namespace", e);
                    }
                });

            return new Vault(vaultConfig);
        }
    }
}
