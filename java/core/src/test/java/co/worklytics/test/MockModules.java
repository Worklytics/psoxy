package co.worklytics.test;

import co.worklytics.psoxy.Rules1;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

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

    @Module
    public interface ForRules {
        @Provides @Singleton
        static Rules1 rules() {
            return mock(Rules1.class);
        }
    }

    @Module
    public interface ForSourceAuthStrategySet {
        @Provides @IntoSet
        static SourceAuthStrategy sourceStratey() {
            return mock(SourceAuthStrategy.class);
        }
    }

}

