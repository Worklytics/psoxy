package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.WritableConfigService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Ticker;
import co.worklytics.psoxy.gateway.TransientConfigException;

import static org.junit.jupiter.api.Assertions.*;

class CachingConfigServiceDecoratorTest {


    LocalHashMapConfigService localHashMapConfigService;
    WritableConfigService config;

    @BeforeEach
    public void setup() {
        localHashMapConfigService = new LocalHashMapConfigService();
        config = new CachingConfigServiceDecorator(localHashMapConfigService, Duration.ofMinutes(1));
    }

    @AllArgsConstructor
    enum TestConfigProperties implements ConfigService.ConfigProperty {
        EXAMPLE_PROPERTY(false),
        NO_CACHE(true),
        ;

        private final Boolean noCache;

        public Boolean noCache() {
            return noCache;
        }
    }

    @SneakyThrows
    @Test
    void putConfigProperty() {
        assertTrue(config.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY).isEmpty());

        assertThrows(NoSuchElementException.class,
            () -> config.getConfigPropertyOrError(TestConfigProperties.EXAMPLE_PROPERTY));

        //read from underlying cache
        assertEquals(1, localHashMapConfigService.getReads());

        //negatively cached
        assertEquals(CachingConfigServiceDecorator.NEGATIVE_VALUE,
            ((CachingConfigServiceDecorator) config).getCache().get(TestConfigProperties.EXAMPLE_PROPERTY));

        config.putConfigProperty(TestConfigProperties.EXAMPLE_PROPERTY, "value");

        assertEquals("value", config.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY).get());
        assertEquals("value", config.getConfigPropertyOrError(TestConfigProperties.EXAMPLE_PROPERTY));

        // still only called that first time
        assertEquals(1, localHashMapConfigService.getReads());

        // written exactly once
        assertEquals(1, localHashMapConfigService.getWrites());

        // value in underlying config
        assertEquals("value",
            localHashMapConfigService.getConfigPropertyOrError(TestConfigProperties.EXAMPLE_PROPERTY));
    }

    @Test
    void retainsStaleValueOnTransientReloadFailure() {
        FakeTicker ticker = new FakeTicker();
        ToggleableConfigService delegate = new ToggleableConfigService();
        delegate.putConfigProperty(TestConfigProperties.EXAMPLE_PROPERTY, "valid_token");

        CachingConfigServiceDecorator cache =
            new CachingConfigServiceDecorator(delegate, Duration.ofMinutes(1), ticker);

        // initial load succeeds
        assertEquals(Optional.of("valid_token"),
            cache.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY));
        assertEquals(1, delegate.getReads());

        // simulate transient SSM failure and advance past TTL
        delegate.setSimulateFailure(true);
        ticker.advance(2, TimeUnit.MINUTES);

        // reload fails silently; old value is retained — caller sees no error
        assertEquals(Optional.of("valid_token"),
            cache.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY));
        assertEquals(2, delegate.getReads()); // reload was attempted

        // SSM recovers; advance past TTL again
        delegate.setSimulateFailure(false);
        ticker.advance(2, TimeUnit.MINUTES);

        // next reload succeeds; value still valid
        assertEquals(Optional.of("valid_token"),
            cache.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY));
        assertEquals(3, delegate.getReads());
    }

    @Test
    void retainsStaleValueOnExplicitTransientException() {
        FakeTicker ticker = new FakeTicker();
        ThrowingConfigService delegate = new ThrowingConfigService("valid_token");

        CachingConfigServiceDecorator cache =
            new CachingConfigServiceDecorator(delegate, Duration.ofMinutes(1), ticker);

        // initial load succeeds
        assertEquals(Optional.of("valid_token"),
            cache.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY));
        assertEquals(1, delegate.getReads());

        // backend starts throwing TransientConfigException
        delegate.setThrowTransient(true);
        ticker.advance(2, TimeUnit.MINUTES);

        // reload catches TransientConfigException; old value retained, no error to caller
        assertEquals(Optional.of("valid_token"),
            cache.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY));
        assertEquals(2, delegate.getReads());

        // backend recovers
        delegate.setThrowTransient(false);
        ticker.advance(2, TimeUnit.MINUTES);

        assertEquals(Optional.of("valid_token"),
            cache.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY));
        assertEquals(3, delegate.getReads());
    }

    @Test
    void transientExceptionOnColdStartDoesNotCacheNegativeValue() {
        FakeTicker ticker = new FakeTicker();
        ThrowingConfigService delegate = new ThrowingConfigService("valid_token");
        delegate.setThrowTransient(true);

        CachingConfigServiceDecorator cache =
            new CachingConfigServiceDecorator(delegate, Duration.ofMinutes(1), ticker);

        // cold start with transient error: returns empty but does NOT cache NEGATIVE_VALUE
        assertEquals(Optional.empty(),
            cache.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY));
        assertEquals(1, delegate.getReads());

        // next request retries immediately (nothing cached) — still failing
        assertEquals(Optional.empty(),
            cache.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY));
        assertEquals(2, delegate.getReads()); // retried, not served from cache

        // backend recovers (no TTL advance needed — nothing was cached)
        delegate.setThrowTransient(false);
        assertEquals(Optional.of("valid_token"),
            cache.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY));
        assertEquals(3, delegate.getReads());
    }

    @Test
    void getConfigProperty_noCache() {
        assertTrue(config.getConfigPropertyAsOptional(TestConfigProperties.NO_CACHE).isEmpty());
        assertEquals(1, localHashMapConfigService.getReads());


        //after write, still goes to origin
        config.putConfigProperty(TestConfigProperties.NO_CACHE, "value");
        assertEquals("value",
            config.getConfigPropertyOrError(TestConfigProperties.NO_CACHE));
        assertEquals(2, localHashMapConfigService.getReads());

        // after read, still goes to origin
        assertEquals("value",
            config.getConfigPropertyOrError(TestConfigProperties.NO_CACHE));
        assertEquals(3, localHashMapConfigService.getReads());
    }


    static class FakeTicker extends Ticker {
        private long nanos = 0;

        @Override
        public long read() {
            return nanos;
        }

        void advance(long amount, TimeUnit unit) {
            nanos += unit.toNanos(amount);
        }
    }

    static class ThrowingConfigService implements WritableConfigService {
        private String value;
        private boolean throwTransient = false;

        @Getter
        private int reads = 0;

        ThrowingConfigService(String value) {
            this.value = value;
        }

        void setThrowTransient(boolean throwTransient) {
            this.throwTransient = throwTransient;
        }

        @Override
        public void putConfigProperty(ConfigProperty property, String newValue) {
            this.value = newValue;
        }

        @Override
        public String getConfigPropertyOrError(ConfigProperty property) {
            return getConfigPropertyAsOptional(property)
                .orElseThrow(() -> new NoSuchElementException("no value for " + property));
        }

        @Override
        public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
            reads++;
            if (throwTransient) {
                throw new TransientConfigException("simulated transient failure");
            }
            return Optional.ofNullable(value);
        }
    }

    static class ToggleableConfigService implements WritableConfigService {
        private final Map<ConfigProperty, String> map = new HashMap<>();

        @Getter
        private int reads = 0;

        private boolean simulateFailure = false;

        void setSimulateFailure(boolean simulateFailure) {
            this.simulateFailure = simulateFailure;
        }

        @Override
        public void putConfigProperty(ConfigProperty property, String value) {
            map.put(property, value);
        }

        @Override
        public String getConfigPropertyOrError(ConfigProperty property) {
            return getConfigPropertyAsOptional(property)
                .orElseThrow(() -> new NoSuchElementException("no value for " + property));
        }

        @Override
        public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
            reads++;
            if (simulateFailure) {
                return Optional.empty();
            }
            return Optional.ofNullable(map.get(property));
        }
    }

    static class LocalHashMapConfigService implements WritableConfigService {

        Map<ConfigProperty, String> map = new HashMap<>();

        @Getter
        int reads = 0;

        @Getter
        int writes = 0;

        @Override
        public void putConfigProperty(ConfigProperty property, String value) {
            writes++;
            map.put(property, value);
        }

        @Override
        public String getConfigPropertyOrError(ConfigProperty property) {
            reads++;
            if (map.containsKey(property)) {
                return map.get(property);
            } else {
                throw new NoSuchElementException("Psoxy misconfigured. Expected value for: " + property.name());
            }
        }

        @Override
        public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
            reads++;
            return Optional.ofNullable(map.get(property));
        }

    }
}
