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
