package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.impl.CachingConfigServiceDecorator;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.VaultConfigService;
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

    //NOTE: also used in SecretsManager case for simplicity
    static String asParameterStoreNamespace(String functionName) {
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
    @Named("Native")
    @Singleton
    static ConfigService nativeConfigService(HostEnvironment hostEnvironment,
                                             EnvVarsConfigService envVarsConfigService,
                                             ParameterStoreConfigServiceFactory parameterStoreConfigServiceFactory,
                                             @Named("instance") ParameterStoreConfigService instanceScopedParameterConfigService,
                                             SecretsManagerConfigServiceFactory secretsManagerConfigServiceFactory,
                                             @Named("instance") SecretsManagerConfigService instanceScopedSecretsManagerConfigService) {

        String pathToSharedConfig =
                envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_SHARED_CONFIG)
                        .orElse(null);

        ParameterStoreConfigService sharedParameterConfigService =
                parameterStoreConfigServiceFactory.create(pathToSharedConfig);

        AwsEnvironment.SecretStoreImplementations secretStoreImpl = envVarsConfigService.getConfigPropertyAsOptional(AwsEnvironment.AwsConfigProperty.SECRETS_STORE)
            .map(AwsEnvironment.SecretStoreImplementations::valueOf)
            .orElse(AwsEnvironment.SecretStoreImplementations.AWS_SSM_PARAMETER_STORE);

        ConfigService sharedConfigService;
        ConfigService instanceConfigService;
        if (secretStoreImpl == AwsEnvironment.SecretStoreImplementations.AWS_SECRETS_MANAGER) {
            instanceConfigService = CompositeConfigService.builder()
                .preferred(instanceScopedSecretsManagerConfigService)
                .fallback(instanceScopedParameterConfigService)
                .build();
            sharedConfigService = CompositeConfigService.builder()
                .preferred(secretsManagerConfigServiceFactory.create(pathToSharedConfig))
                .fallback(sharedParameterConfigService)
                .build();
        } else if (secretStoreImpl == AwsEnvironment.SecretStoreImplementations.AWS_SSM_PARAMETER_STORE
                || secretStoreImpl == AwsEnvironment.SecretStoreImplementations.HASHICORP_VAULT) {
            /**
             * Vault is wrapped over the 'Native' secrets manager in FunctionRuntimeModule
             *
             * TODO: probably consolidate this all in v0.5, when we eliminate fallback configs
             * (where values in env vars take precedence over those in remote config stores;
             *  and remote config store may be composite of parameter store + secrets manager)
             *
             * @see co.worklytics.psoxy.FunctionRuntimeModule
             */
            instanceConfigService = instanceScopedParameterConfigService;
            sharedConfigService = sharedParameterConfigService;
        } else {
            throw new IllegalStateException("Unknown secret store implementation: " + secretStoreImpl);
        }

        Duration proxyInstanceConfigCacheTtl = Duration.ofMinutes(5);
        Duration sharedConfigCacheTtl = Duration.ofMinutes(20);
        return CompositeConfigService.builder()
                .preferred(new CachingConfigServiceDecorator(instanceConfigService, proxyInstanceConfigCacheTtl))
                .fallback(new CachingConfigServiceDecorator(sharedConfigService, sharedConfigCacheTtl))
                .build();
    }

    @Provides
    @Named("instance")
    static ParameterStoreConfigService instanceConfigService(HostEnvironment hostEnvironment,
                                                      EnvVarsConfigService envVarsConfigService,
                                                      ParameterStoreConfigServiceFactory parameterStoreConfigServiceFactory) {

        String pathToInstanceConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asParameterStoreNamespace(hostEnvironment.getInstanceId()) + "_");

        return parameterStoreConfigServiceFactory.create(pathToInstanceConfig);
    }

    @Provides
    @Named("instance")
    static SecretsManagerConfigService instanceSecretsManagerConfigService(HostEnvironment hostEnvironment,
                                                                            EnvVarsConfigService envVarsConfigService,
                                                             SecretsManagerConfigServiceFactory secretsManagerConfigServiceFactory) {

        String pathToInstanceConfig =
            envVarsConfigService.getConfigPropertyAsOptional(ProxyConfigProperty.PATH_TO_INSTANCE_CONFIG)
                .orElseGet(() -> asParameterStoreNamespace(hostEnvironment.getInstanceId()) + "_");

        return secretsManagerConfigServiceFactory.create(pathToInstanceConfig);
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
