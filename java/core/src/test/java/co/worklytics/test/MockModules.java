package co.worklytics.test;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

import javax.inject.Singleton;

import java.util.Random;

import static org.mockito.Mockito.mock;

public class MockModules {

    @Module
    public interface ForConfigService {
        @Provides @Singleton
        static ConfigService configService() {
            ConfigService mock = mock(ConfigService.class);
            TestModules.withMockEncryptionKey(mock);
            return mock;
        }
    }

    @Module
    public interface ForRandomNumberGenerator {
        @Provides @Singleton
        static RandomNumberGenerator random() {
            //NOTE: only works for jdk17+ with Mockito versions that include fix for:
            // https://github.com/mockito/mockito/issues/2589
            // --> possibly not coming until Mockito 5????
            //return mock(new Random());

            return mock(RandomNumberGenerator.class);
        }
    }

    @Module
    public interface ForRules {
        @Provides @Singleton
        static RuleSet rules() {
            return mock(RuleSet.class);
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

