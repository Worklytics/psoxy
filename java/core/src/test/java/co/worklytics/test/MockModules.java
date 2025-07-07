package co.worklytics.test;

import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import co.worklytics.psoxy.gateway.impl.output.NoOutput;
import co.worklytics.psoxy.gateway.output.ApiDataSideOutput;
import co.worklytics.psoxy.gateway.output.ApiSanitizedDataOutput;
import co.worklytics.psoxy.gateway.output.OutputFactory;
import co.worklytics.psoxy.gateway.output.OutputLocation;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import com.avaulta.gateway.rules.BulkDataRules;
import com.avaulta.gateway.rules.ColumnarRules;
import com.avaulta.gateway.rules.RecordRules;
import com.avaulta.gateway.rules.RuleSet;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.auth.http.HttpTransportFactory;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang3.JavaVersion;
import org.apache.commons.lang3.SystemUtils;
import org.mockito.MockMakers;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.io.IOException;

import static org.mockito.Mockito.*;

public class MockModules {


    /**
     * helper to downgrade behavior of mockito to pre-java17 behavior
     */
    public static boolean isAtLeastJava17(){
        return SystemUtils.isJavaVersionAtLeast(JavaVersion.JAVA_17);
    }

    public static <C> C provideMock(Class<C> clazz) {
        if (isAtLeastJava17()) {
            return mock(clazz, withSettings().mockMaker(MockMakers.SUBCLASS));
        } else {
            return mock(clazz);
        }
    }

    @Module
    public interface ForConfigService {
        @Provides @Singleton
        static ConfigService configService() {
            ConfigService mock = provideMock(ConfigService.class);
            return mock;
        }
    }

    @Module
    public interface ForSecretStore {
        @Provides @Singleton
        static SecretStore secretStore() {
            SecretStore mock = provideMock(SecretStore.class);
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
            //return mock(Random.class);
            return provideMock(RandomNumberGenerator.class);
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
            return provideMock(ColumnarRules.class);
        }

        @Provides @Singleton
        static RESTRules restRules() {
            return provideMock(RESTRules.class);
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
            return MockModules.provideMock(HttpTransportFactory.class);
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
            HostEnvironment hostEnvironment = () -> "psoxy-test";
            return hostEnvironment;
        }
    }



    @Module
    public interface ForSideOutputs {

        // so actually, not mocks ...

        @Provides @Named("forOriginal") @Singleton
        static ApiDataSideOutput sideOutputForOriginal() {
            return new NoApiDataSideOutput();
        }

        @Provides @Named("forSanitized") @Singleton
        static ApiDataSideOutput sideOutputForSanitized() {
            return new NoApiDataSideOutput();
        }


        @Provides @Named("async") @Singleton
        static ApiSanitizedDataOutput apiSanitizedDataOutput() {
            return new NoApiDataSideOutput();
        }

        @Provides
        static NoOutput providesNoOutput() {
            return new NoOutput();
        }

        @Provides @IntoSet
        static OutputFactory<?> providesOutputFactory() {
            return new OutputFactory<NoOutput>() {
                @Override
                public NoOutput create(OutputLocation outputLocation) {
                    return new NoOutput();
                }

                @Override
                public boolean supports(OutputLocation outputLocation) {
                    return true;
                }
            };
        }


        /**
         * a no-op implementation of SideOutput that does nothing.
         */
        @NoArgsConstructor(onConstructor_ = {@Inject})
        class NoApiDataSideOutput implements ApiDataSideOutput {

            @Override
            public void writeRaw(ProcessedContent content, ApiDataRequestHandler.ProcessingContext processingContext) throws IOException {

            }

            @Override
            public void writeSanitized(ProcessedContent content, ApiDataRequestHandler.ProcessingContext processingContext) throws IOException {

            }
        }
    }

    @Module
    public interface ForAsyncApiDataRequestHandler {

        @Provides
        @Singleton
        static AsyncApiDataRequestHandler asyncApiDataRequestHandler() {
            return mock(AsyncApiDataRequestHandler.class);
        }
    }
}

