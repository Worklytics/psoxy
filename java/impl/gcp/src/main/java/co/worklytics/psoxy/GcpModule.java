package co.worklytics.psoxy;


import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.Binds;
import dagger.Module;

@Module(
    includes = CoreModule.class
)
public interface GcpModule {

    @Binds ConfigService configService(EnvVarsConfigService envVarsConfigService);
}
