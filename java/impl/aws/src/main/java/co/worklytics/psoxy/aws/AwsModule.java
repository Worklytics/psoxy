package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.*;
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
                                       ParameterStoreConfigService functionScopedParameterStoreConfigService
                                      ) {
        Duration sharedTtl = Duration.ofMinutes(20);
        Duration connectorTtl = Duration.ofMinutes(5);
        CompositeConfigService parameterStoreConfigHierarchy = CompositeConfigService.builder()
            .fallback(new CachingConfigServiceDecorator(globalParameterStoreConfigService, sharedTtl))
            .preferred(new CachingConfigServiceDecorator(functionScopedParameterStoreConfigService, connectorTtl))
            .build();

        return CompositeConfigService.builder()
            .fallback(parameterStoreConfigHierarchy)
            .preferred(envVarsConfigService)
            .build();
    }

    @Provides
    static AmazonS3 getStorageClient() {
        return AmazonS3ClientBuilder.defaultClient();
    }
}
