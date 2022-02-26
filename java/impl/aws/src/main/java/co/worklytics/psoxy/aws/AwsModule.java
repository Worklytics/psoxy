package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import javax.inject.Named;


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
            .region(region)
            .build();
    }

    //global parameters
    @Provides @Named("Global")
    static ParameterStoreConfigService parameterStoreConfigService(SsmClient ssmClient) {
        return new ParameterStoreConfigService("", ssmClient);
    }

    //parameters scoped to function
    @Provides
    static ParameterStoreConfigService functionParameterStoreConfigService(SsmClient ssmClient) {
        String namespace =
            asParameterStoreNamespace(System.getenv(RuntimeEnvironmentVariables.AWS_LAMBDA_FUNCTION_NAME.name()));
        return new ParameterStoreConfigService(namespace, ssmClient);
    }

    static String asParameterStoreNamespace(String functionName) {
        return functionName.toUpperCase().replace("-", "_") + "_";
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
