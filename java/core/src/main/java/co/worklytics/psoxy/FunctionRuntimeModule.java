package co.worklytics.psoxy;

import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.time.Clock;
import java.util.UUID;

/**
 * generic dep bindings for an actual Function runtime environment (as opposed to a testing runtime)
 *
 *
 */
@Module
public class FunctionRuntimeModule {

    @Provides
    @Singleton
    static Clock clock() {
        return Clock.systemUTC();
    }

    @Provides
    static UUID randomUUID() {
        return UUID.randomUUID();
    }

    @Provides
    static HttpRequestFactory providesHttpRequestFactory() {
        //atm, all function runtimes expected to use generic java NetHttpTransport
        return (new NetHttpTransport()).createRequestFactory();
    }

}
