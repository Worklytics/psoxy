package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.impl.BlindlyOptimisticLockService;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import javax.inject.Singleton;

@NoArgsConstructor
@AllArgsConstructor
@Module(
   includes = CmdLineModule.Bindings.class
)
public class CmdLineModule {


    String[] args;


    @Provides @Singleton
    ConfigService configService(CommandLineConfigServiceFactory factory) {
        return factory.create(args);
    }

    @Provides @Singleton
    SecretStore secretStore(CommandLineConfigServiceFactory factory) {
        return factory.create(args);
    }

    @Module
    interface Bindings {

        @Binds
        LockService lockService(BlindlyOptimisticLockService impl);

    }

}
