package co.worklytics.psoxy.gateway.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import javax.inject.Singleton;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.RESTApiSanitizer;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.ApiModeConfigProperty;
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
 * under concurrent access. We call loadSanitizerRules() directly via reflection since it is
 * private and is the method that contains the DCL pattern we are testing.
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

    private Method loadSanitizerRulesMethod;

    @BeforeEach
    public void setup() throws NoSuchMethodException {
        Container container = DaggerApiDataRequestHandlerConcurrencyTest_Container.create();
        container.inject(this);

        when(handler.secretStore
            .getConfigPropertyAsOptional(eq(ProxyConfigProperty.PSOXY_SALT)))
            .thenReturn(Optional.of("salt"));
        when(handler.config.getConfigPropertyOrError(eq(ProxyConfigProperty.SOURCE)))
            .thenReturn("gmail");
        when(handler.config.getConfigPropertyOrError(ApiModeConfigProperty.TARGET_HOST))
            .thenReturn("gmail.googleapis.com");

        // Get access to the private loadSanitizerRules() method which contains the DCL
        loadSanitizerRulesMethod = ApiDataRequestHandler.class.getDeclaredMethod("loadSanitizerRules");
        loadSanitizerRulesMethod.setAccessible(true);
    }

    /**
     * Two threads race to call loadSanitizerRules() which contains the DCL pattern.
     * Both must get a valid, non-null sanitizer, and both must get the same instance
     * (the DCL should only create it once).
     */
    @SneakyThrows
    @RepeatedTest(10)
    void loadSanitizerRules_threadSafe() {
        // ensure sanitizer is null before each repetition
        handler.sanitizer = null;

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<RESTApiSanitizer> result1 = new AtomicReference<>();
        AtomicReference<RESTApiSanitizer> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                result1.set((RESTApiSanitizer) loadSanitizerRulesMethod.invoke(handler));
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                result2.set((RESTApiSanitizer) loadSanitizerRulesMethod.invoke(handler));
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

        // DCL should produce the same instance — only one thread creates it
        assertTrue(result1.get() == result2.get(),
            "Both threads should get the same sanitizer instance from DCL");

        // The shared field should be set
        assertNotNull(handler.sanitizer, "Shared sanitizer field should be initialized after concurrent access");
    }
}
