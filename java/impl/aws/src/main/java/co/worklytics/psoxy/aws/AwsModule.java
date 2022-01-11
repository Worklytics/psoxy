package co.worklytics.psoxy.aws;


import co.worklytics.psoxy.CoreModule;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;

@Module(
    includes = CoreModule.class
)
public interface AwsModule {

    enum AwsConfig implements ConfigService.ConfigProperty {
        REGION,
    }

    @Binds ConfigService configService(EnvVarsConfigService envVarsConfigService);

    @Provides static SsmClient ssmClient(EnvVarsConfigService envVarsConfigService) {
        Region region = Region.of(envVarsConfigService.getConfigPropertyOrError(AwsConfig.REGION));

        return SsmClient.builder()
            .region(region)
            .build();
    }
}
