package co.worklytics.psoxy.aws;


import co.worklytics.psoxy.CoreModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.CompositeConfigService;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

import javax.inject.Singleton;

@Module(
    includes = CoreModule.class
)
public interface AwsModule {

    enum AwsConfig implements ConfigService.ConfigProperty {
        REGION,
    }

    @Provides @Singleton
    static ConfigService configService(EnvVarsConfigService envVarsConfigService,
                                       ParameterStoreConfigService parameterStoreConfigService) {

        return CompositeConfigService.builder()
            .fallback(parameterStoreConfigService)
            .preferred(envVarsConfigService)
            .build();

    };

    @Provides
    static SsmClient ssmClient(EnvVarsConfigService envVarsConfigService) {
        Region region = Region.of(envVarsConfigService.getConfigPropertyOrError(AwsConfig.REGION));

        return SsmClient.builder()
            .region(region)
            .build();
    }
}
