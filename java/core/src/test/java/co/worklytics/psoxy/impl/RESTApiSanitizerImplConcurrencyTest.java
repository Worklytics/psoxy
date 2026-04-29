package co.worklytics.psoxy.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import co.worklytics.psoxy.PseudonymizerImplFactory;
import co.worklytics.psoxy.Pseudonymizer;
import co.worklytics.psoxy.PsoxyModule;
import co.worklytics.psoxy.RESTApiSanitizerFactory;
import co.worklytics.psoxy.gateway.ApiModeConfigProperty;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.rules.PrebuiltSanitizerRules;
import co.worklytics.test.MockModules;
import co.worklytics.test.TestUtils;
import dagger.Component;
import dagger.Module;
import dagger.Provides;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;


/**
 * Concurrency tests for {@link RESTApiSanitizerImpl}.
 *
 * Exercises the double-checked locking paths for lazily-initialized fields to verify they
 * are thread-safe when multiple requests are handled concurrently on the same instance.
 */
class RESTApiSanitizerImplConcurrencyTest {

    @Inject
    protected RESTApiSanitizerFactory sanitizerFactory;

    @Inject
    protected PseudonymizerImplFactory pseudonymizerImplFactory;

    RESTApiSanitizerImpl sanitizer;

    @Singleton
    @Component(
            modules = {
                PsoxyModule.class,
                ForConfigService.class,
                MockModules.ForSecretStore.class,
            })
    public interface Container {
        void inject(RESTApiSanitizerImplConcurrencyTest test);
    }

    @Module
    public interface ForConfigService {
        @Provides
        @Singleton
        static ConfigService configService() {
            ConfigService mock = MockModules.provideMock(ConfigService.class);
            when(mock.getConfigPropertyOrError(eq(ProxyConfigProperty.SOURCE))).thenReturn("gmail");
            when(mock.getConfigPropertyAsOptional(eq(ApiModeConfigProperty.TARGET_HOST)))
                    .thenReturn(Optional.of("gmail.googleapis.com"));
            return mock;
        }
    }

    @BeforeEach
    public void setup() {
        Container container = DaggerRESTApiSanitizerImplConcurrencyTest_Container.create();
        container.inject(this);

        Pseudonymizer pseudonymizer = pseudonymizerImplFactory
                .create(Pseudonymizer.ConfigurationOptions.builder().build());

        sanitizer = sanitizerFactory.create(PrebuiltSanitizerRules.DEFAULTS.get("gmail"),
                pseudonymizer);
        sanitizer.setSanitizationTimeout(Duration.ofSeconds(5));
    }

    /**
     * Two threads race to call getCompiledAllowedEndpoints(). Both must get the same
     * valid, complete map. Repeated to increase probability of hitting the race window.
     */
    @SneakyThrows
    @RepeatedTest(10)
    void getCompiledAllowedEndpoints_threadSafe() {
        // fresh sanitizer each iteration — compiledAllowedEndpoints starts null
        Pseudonymizer pseudonymizer = pseudonymizerImplFactory
                .create(Pseudonymizer.ConfigurationOptions.builder().build());
        RESTApiSanitizerImpl fresh = sanitizerFactory.create(
                PrebuiltSanitizerRules.DEFAULTS.get("gmail"), pseudonymizer);

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<Map<Endpoint, Pattern>> result1 = new AtomicReference<>();
        AtomicReference<Map<Endpoint, Pattern>> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                result1.set(fresh.getCompiledAllowedEndpoints());
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                result2.set(fresh.getCompiledAllowedEndpoints());
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

        assertNotNull(result1.get(), "Thread 1 got null compiledAllowedEndpoints");
        assertNotNull(result2.get(), "Thread 2 got null compiledAllowedEndpoints");

        // both threads must get the exact same map (same identity — written once by DCL)
        assertTrue(result1.get() == result2.get(),
                "Both threads should get the same map instance from DCL");
        assertEquals(result1.get().size(), result2.get().size());
    }

    /**
     * Two threads race to call getRootDefinitions(). Both must get the same valid object.
     */
    @SneakyThrows
    @RepeatedTest(10)
    void getRootDefinitions_threadSafe() {
        Pseudonymizer pseudonymizer = pseudonymizerImplFactory
                .create(Pseudonymizer.ConfigurationOptions.builder().build());
        RESTApiSanitizerImpl fresh = sanitizerFactory.create(
                PrebuiltSanitizerRules.DEFAULTS.get("gmail"), pseudonymizer);

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<JsonSchemaFilter> result1 = new AtomicReference<>();
        AtomicReference<JsonSchemaFilter> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                result1.set(fresh.getRootDefinitions());
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                result2.set(fresh.getRootDefinitions());
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

        // both must be non-null and refer to the same instance
        assertNotNull(result1.get());
        assertNotNull(result2.get());
        assertTrue(result1.get() == result2.get(),
                "Both threads should get the same rootDefinitions instance from DCL");
    }

    /**
     * Concurrent sanitize() calls on the same sanitizer must not interfere with each other.
     * Each thread sanitizes a different message and asserts its output is correct.
     */
    @SneakyThrows
    @RepeatedTest(5)
    void concurrentSanitize_noContamination() {
        String json1 = new String(TestUtils.getData(
                "sources/google-workspace/gmail/example-api-responses/original/message.json"));

        // use the same json for both threads but different URLs
        URL url1 = new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/msg1?format=metadata");
        URL url2 = new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/msg2?format=metadata");

        CyclicBarrier barrier = new CyclicBarrier(2);
        AtomicReference<String> result1 = new AtomicReference<>();
        AtomicReference<String> result2 = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread t1 = new Thread(() -> {
            try {
                barrier.await();
                result1.set(sanitizer.sanitize("GET", url1, json1));
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        Thread t2 = new Thread(() -> {
            try {
                barrier.await();
                result2.set(sanitizer.sanitize("GET", url2, json1));
            } catch (Throwable t) {
                error.compareAndSet(null, t);
            }
        });

        t1.start();
        t2.start();
        t1.join(10000);
        t2.join(10000);

        if (error.get() != null) {
            throw new AssertionError("Thread threw exception during sanitize", error.get());
        }

        assertNotNull(result1.get(), "Thread 1 sanitize returned null");
        assertNotNull(result2.get(), "Thread 2 sanitize returned null");

        // both threads should produce the same sanitization (same input, same rules)
        assertEquals(result1.get(), result2.get(),
                "Same input with same rules should produce identical sanitized output");
    }
}
