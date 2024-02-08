package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.impl.BlindlyOptimisticLockService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.Optional;

@Module
public interface CmdLineModule {

    @Binds
    ConfigService configService(EnvVarsConfigService impl);

    @Binds
    LockService lockService(BlindlyOptimisticLockService impl);

    @Provides @Singleton
    static SecretStore secretStore(EnvVarsConfigService envVarsConfigService) {

        //proxy to env vars
        return new SecretStore() {
            @Override
            public void putConfigProperty(ConfigProperty property, String value) {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getConfigPropertyOrError(ConfigProperty property) {
                return envVarsConfigService.getConfigPropertyOrError(property);
            }

            @Override
            public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
                return envVarsConfigService.getConfigPropertyAsOptional(property);
            }
        };
    }

}
