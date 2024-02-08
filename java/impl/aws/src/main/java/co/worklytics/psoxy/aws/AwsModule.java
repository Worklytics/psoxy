package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.*;
import co.worklytics.psoxy.gateway.impl.oauth.OAuthRefreshTokenSourceAuthStrategy;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.BackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentity.CognitoIdentityClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ssm.SsmClient;

import javax.inject.Named;
import javax.inject.Singleton;
import java.time.Duration;


/**
 * defines how to fulfill dependencies that need platform-specific implementations for GCP platform
 */
@Module
public interface AwsModule {

    @Provides
    @Singleton
    static AwsEnvironment awsEnvironment() {
        return new AwsEnvironment();
    }

    @Provides
    @Singleton
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

    /**
     * used to prefix function's "local" config in a way that is compliant with AWS roles for
     * SSM parameter paths and Secrets Manager secret names
     *
     * @param functionName
     * @return function name formated for use as AWS ssm parameter path or secrets manager secret prefix
     */
    static String asAwsCompliantNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_");
    }

    @Provides
    @Singleton
    static CognitoIdentityClient cognitoClient(AwsEnvironment awsEnvironment) {
        AWSCredentials credentials = DefaultAWSCredentialsProviderChain.getInstance().getCredentials();

        return CognitoIdentityClient.builder()
                .region(Region.of(awsEnvironment.getRegion()))
                .build();
    }

    @Provides
    static SecretStore secretStore(HostEnvironment hostEnvironment,
                                   EnvVarsConfigService envVarsConfigService,
                                   ParameterStoreConfigServiceFactory parameterStoreConfigServiceFactory,
                                   @Named("instance") ParameterStoreConfigService instanceScopedParameterConfigService,
                                   SecretsManagerSecretStoreFactory secretsManagerSecretStoreFactory,
                                   VaultConfigServiceFactory vaultSecretStoreFactory) {

        String pathToSharedConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                .orElse(null);

        String pathToInstanceConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asAwsCompliantNamespace(hostEnvironment.getInstanceId()) + "_");


        AwsEnvironment.SecretStoreImplementations secretStoreImpl = envVarsConfigService.getConfigPropertyAsOptional(AwsEnvironment.AwsConfigProperty.SECRETS_STORE)
            .map(String::toUpperCase) //case-insensitive, so accept 'aws_ssm_parameter_store'
            .map(AwsEnvironment.SecretStoreImplementations::valueOf)
            .orElse(AwsEnvironment.SecretStoreImplementations.AWS_SSM_PARAMETER_STORE);

        SecretStore sharedConfigService;
        SecretStore instanceConfigService;
        if (secretStoreImpl == AwsEnvironment.SecretStoreImplementations.AWS_SECRETS_MANAGER) {
            instanceConfigService = secretsManagerSecretStoreFactory.create(pathToInstanceConfig);
            sharedConfigService = secretsManagerSecretStoreFactory.create(pathToSharedConfig);
        } else if (secretStoreImpl == AwsEnvironment.SecretStoreImplementations.AWS_SSM_PARAMETER_STORE) {
            instanceConfigService = instanceScopedParameterConfigService;
            sharedConfigService = parameterStoreConfigServiceFactory.create(pathToSharedConfig);
        } else if (secretStoreImpl == AwsEnvironment.SecretStoreImplementations.HASHICORP_VAULT) {
            sharedConfigService =
                vaultSecretStoreFactory.createInitialized(vaultSecretStoreFactory.pathForSharedVault(hostEnvironment, envVarsConfigService));
            instanceConfigService =
                vaultSecretStoreFactory.createInitialized(vaultSecretStoreFactory.pathForInstanceVault(hostEnvironment, envVarsConfigService));
        } else {
            throw new IllegalStateException("Unknown secret store implementation: " + secretStoreImpl);
        }

        Duration proxyInstanceConfigCacheTtl = Duration.ofMinutes(5);
        Duration sharedConfigCacheTtl = Duration.ofMinutes(20);

        return CompositeSecretStore.builder()
            .preferred(new CachingConfigServiceDecorator(instanceConfigService, proxyInstanceConfigCacheTtl))
            .fallback(new CachingConfigServiceDecorator(sharedConfigService, sharedConfigCacheTtl))
            .build();
    }

    @Provides
    @Named("Native")
    @Singleton
    static ConfigService nativeConfigService(EnvVarsConfigService envVarsConfigService,
                                             ParameterStoreConfigServiceFactory parameterStoreConfigServiceFactory,
                                             @Named("instance") ParameterStoreConfigService instanceScopedParameterConfigService) {

        String pathToSharedConfig =
                envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                        .orElse(null);
        parameterStoreConfigServiceFactory.create(pathToSharedConfig);

        ParameterStoreConfigService sharedParameterConfigService =
                parameterStoreConfigServiceFactory.create(pathToSharedConfig);

        Duration proxyInstanceConfigCacheTtl = Duration.ofMinutes(5);
        Duration sharedConfigCacheTtl = Duration.ofMinutes(20);
        return CompositeConfigService.builder()
                .preferred(new CachingConfigServiceDecorator(instanceScopedParameterConfigService, proxyInstanceConfigCacheTtl))
                .fallback(new CachingConfigServiceDecorator(sharedParameterConfigService, sharedConfigCacheTtl))
                .build();
    }

    @Provides
    @Named("instance")
    static ParameterStoreConfigService instanceConfigService(HostEnvironment hostEnvironment,
                                                      EnvVarsConfigService envVarsConfigService,
                                                      ParameterStoreConfigServiceFactory parameterStoreConfigServiceFactory) {

        String pathToInstanceConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asAwsCompliantNamespace(hostEnvironment.getInstanceId()) + "_");

        return parameterStoreConfigServiceFactory.create(pathToInstanceConfig);
    }

    @Provides @Singleton
    static LockService lockService(@Named("instance") ParameterStoreConfigService parameterStoreConfigService) {
        return parameterStoreConfigService;
    }

    @Provides
    static AmazonS3 getStorageClient() {
        return AmazonS3ClientBuilder.defaultClient();
    }

    @Provides
    static SecretsManagerClient secretsManagerClient(AwsEnvironment awsEnvironment) {
        return SecretsManagerClient.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                .retryPolicy(RetryPolicy.builder()
                    .numRetries(4)
                    .throttlingBackoffStrategy(BackoffStrategy.defaultThrottlingStrategy())
                    .build())
                .build())
                .region(Region.of(awsEnvironment.getRegion()))
                .build();
    }

    @Provides
    @Singleton
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

    @Provides
    @IntoSet
    static OAuthRefreshTokenSourceAuthStrategy.TokenRequestBuilder providesSourceAuthStrategy(AWSWorkloadIdentityFederationGrantTokenRequestBuilder tokenRequestBuilder) {
        return tokenRequestBuilder;
    }
}
