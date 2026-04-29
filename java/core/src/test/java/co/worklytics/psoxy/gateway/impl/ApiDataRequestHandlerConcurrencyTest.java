package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.RESTApiSanitizer;
import co.worklytics.psoxy.gateway.ApiModeConfigProperty;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestModules;
import dagger.Component;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;


/**
 * Concurrency tests for {@link ApiDataRequestHandler}.
 *
 * Verifies that the double-checked locking on the lazy-loaded sanitizer field works correctly
 * under concurrent access. We use {@code getSanitizerForRequest()} as the public entry point
 * that triggers the DCL initialization of the sanitizer field.
 */
class ApiDataRequestHandlerConcurrencyTest {

    @Inject
    ApiDataRequestHandler handler;

    @Singleton
    @Component(modules = {
        PsoxyModule.class,
        MockModules.ForConfigService.class,
        MockModules.ForSecretStore.class,
        MockModules.ForRules.class,
        MockModules.ForSourceAuthStrategySet.class,
        MockModules.ForHttpTransportFactory.class,
        MockModules.ForSideOutputs.class,
        MockModules.ForAsyncApiDataRequestHandler.class,
        TestModules.ForWebhookCollectorModeConfig.class,
        TestModules.ForFixedUUID.class,
        TestModules.ForFixedClock.class,
        TestModules.ForProxyConstants.class,
    })
    public interface Container {
        void inject(ApiDataRequestHandlerConcurrencyTest test);
    }

    @BeforeEach
    public void setup() {
        Container container = DaggerApiDataRequestHandlerConcurrencyTest_Container.create();
        container.inject(this);

        when(handler.secretStore
            .getConfigPropertyAsOptional(eq(ProxyConfigProperty.PSOXY_SALT)))
            .thenReturn(Optional.of("salt"));
        when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.SOURCE)))
            .thenReturn("gmail");
        when(handler.config.getConfigPropertyOrError(ApiModeConfigProperty.TARGET_HOST))
            .thenReturn("gmail.googleapis.com");
    }

    /**
     * Two threads race to call getSanitizerForRequest() which triggers the DCL on the
     * sanitizer field. Both must get a valid, non-null sanitizer. Since getSanitizerForRequest()
     * may return different instances (it can create per-request sanitizers for different
     * pseudonym implementations), we verify the underlying shared sanitizer field is consistent.
     */
    @SneakyThrows
    @RepeatedTest(10)
    void getSanitizer_threadSafe() {
        // ensure sanitizer is null before each repetition
        handler.sanitizer = null;

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<RESTApiSanitizer> result1 = new AtomicReference<>();
        AtomicReference<RESTApiSanitizer> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        // Use a mock request that doesn't override pseudonym implementation
        // so both threads get the default sanitizer (which triggers DCL)
        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                result1.set(handler.getSanitizerForRequest(
                    MockModules.provideMock(co.worklytics.psoxy.gateway.HttpEventRequest.class)));
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                result2.set(handler.getSanitizerForRequest(
                    MockModules.provideMock(co.worklytics.psoxy.gateway.HttpEventRequest.class)));
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        t1.start();
        t2.start();
        t1.join(5000);
        t2.join(5000);

        if (error.get() != null) {
            throw new AssertionError("Thread threw exception", error.get());
        }

        assertNotNull(result1.get(), "Thread 1 got null sanitizer");
        assertNotNull(result2.get(), "Thread 2 got null sanitizer");

        // After both threads complete, the handler's shared sanitizer field should be non-null
        assertNotNull(handler.sanitizer, "Shared sanitizer field should be initialized after concurrent access");
    }
}
