package co.worklytics.test;

import co.worklytics.psoxy.gateway.ConfigService;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

import static org.mockito.Mockito.mock;

public class MockModules {

    @Module
    public interface ForConfigService {

        @Provides @Singleton
        static ConfigService configService() {
            return mock(ConfigService.class);
        }
    }

}

