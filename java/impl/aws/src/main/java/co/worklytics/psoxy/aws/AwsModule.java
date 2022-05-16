package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
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

    //global parameters
    @Provides @Named("Global")
    static ParameterStoreConfigService parameterStoreConfigService(SsmClient ssmClient) {
        // Global don't change that often, use longer TTL
        return new ParameterStoreConfigService(null, Duration.ofMinutes(20), ssmClient);
    }

    //parameters scoped to function
    @Provides
    static ParameterStoreConfigService functionParameterStoreConfigService(SsmClient ssmClient) {
        String namespace =
            asParameterStoreNamespace(System.getenv(RuntimeEnvironmentVariables.AWS_LAMBDA_FUNCTION_NAME.name()));
        // Namespaced params may change often (refresh tokens), use shorter TTL
        return new ParameterStoreConfigService(namespace, Duration.ofMinutes(3), ssmClient);
    }

    static String asParameterStoreNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_");
    }

    @Provides
    static ConfigService configService(EnvVarsConfigService envVarsConfigService,
                                       @Named("Global") ParameterStoreConfigService globalParameterStoreConfigService,
                                       ParameterStoreConfigService functionScopedParameterStoreConfigService
                                      ) {

        CompositeConfigService parameterStoreConfigHierarchy = CompositeConfigService.builder()
            .fallback(globalParameterStoreConfigService)
            .preferred(functionScopedParameterStoreConfigService)
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
