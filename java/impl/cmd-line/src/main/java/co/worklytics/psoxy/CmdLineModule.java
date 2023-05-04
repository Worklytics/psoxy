package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.impl.BlindlyOptimisticLockService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.Binds;
import dagger.Module;

@Module
public interface CmdLineModule {

    @Binds
    ConfigService configService(EnvVarsConfigService impl);

    @Binds
    LockService lockService(BlindlyOptimisticLockService impl);
}
