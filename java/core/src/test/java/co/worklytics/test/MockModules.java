package co.worklytics.test;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HostEnvironment;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import com.avaulta.gateway.rules.BulkDataRules;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.RecordRules;
import com.avaulta.gateway.rules.RuleSet;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.http.HttpTransportFactory;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import lombok.SneakyThrows;
import org.mockito.MockMakers;

import javax.inject.Singleton;

import static org.mockito.Mockito.*;

public class MockModules {

    @Module
    public interface ForConfigService {
        @Provides @Singleton
        static ConfigService configService() {
            ConfigService mock = mock(ConfigService.class, withSettings().mockMaker(MockMakers.SUBCLASS));
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

        @Provides @Singleton
        static BulkDataRules bulkDataRules() {
            // why is INLINE mock maker a propblem ehre???
            return mock(ColumnarRules.class, withSettings().mockMaker(MockMakers.SUBCLASS));
        }

        @Provides @Singleton
        static RESTRules restRules() {
            return mock(RESTRules.class, withSettings().mockMaker(MockMakers.SUBCLASS));
        }

        @Provides @Singleton
        static ColumnarRules columnarRules() {
            return mock(ColumnarRules.class);
        }

        @Provides @Singleton
        static RecordRules recordRules() {
            return mock(RecordRules.class);
        }
    }

    @Module
    public interface ForSourceAuthStrategySet {
        @Provides @IntoSet
        static SourceAuthStrategy sourceStratey() {
            return mock(SourceAuthStrategy.class);
        }
    }

    @Module
    public interface ForHttpTransportFactory {
        @Provides @Singleton
        static HttpTransportFactory httpTransportFactory() {
            return mock(HttpTransportFactory.class);
        }

        @SneakyThrows
        public static void mockResponse(HttpTransportFactory factory, String content) {

            MockHttpTransport.Builder builder = new MockHttpTransport.Builder();

            builder.setLowLevelHttpResponse(new MockLowLevelHttpResponse()
                .setStatusCode(200)
                .setContent(content));

            when(factory.create()).thenReturn(builder.build());
        }

    }

    @Module
    public interface ForHostEnvironment {

        @Provides
        @Singleton
        static HostEnvironment hostEnvironment() {
            HostEnvironment hostEnvironment = mock(HostEnvironment.class);
            when(hostEnvironment.getInstanceId()).thenReturn("psoxy-test");
            return hostEnvironment;
        }
    }
}

