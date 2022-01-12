package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dagger.Binds;
import dagger.Module;

import javax.inject.Named;

@Module
public interface CmdLineModule {

    @Binds
    ObjectMapper yamlMapper(@Named("ForYAML") ObjectMapper yamlMapper);

    @Binds
    ConfigService configService(EnvVarsConfigService impl);
}
