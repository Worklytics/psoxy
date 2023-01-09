package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CachingConfigServiceDecoratorTest {


    LocalHashMapConfigService spy;
    ConfigService config;

    @BeforeEach
    public void setup() {
        spy = spy(new LocalHashMapConfigService());
        config = new CachingConfigServiceDecorator(spy, Duration.ofMinutes(1));
    }
    enum TestConfigProperties implements ConfigService.ConfigProperty {
        EXAMPLE_PROPERTY,
        ;
    }

    @SneakyThrows
    @Test
    void putConfigProperty() {
        assertTrue(config.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY).isEmpty());

        //read from underlying cache
        verify(spy, times(1))
            .getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY);

        //negatively cached
        assertEquals(CachingConfigServiceDecorator.NEGATIVE_VALUE,
            ((CachingConfigServiceDecorator) config).getCache().get(TestConfigProperties.EXAMPLE_PROPERTY));

        config.putConfigProperty(TestConfigProperties.EXAMPLE_PROPERTY, "value");

        assertEquals("value", config.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY).get());
        assertEquals("value", config.getConfigPropertyOrError(TestConfigProperties.EXAMPLE_PROPERTY));

        // still only called that first time
        verify(spy, times(1))
            .getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY);

        // written exactly once
        verify(spy, times(1))
            .putConfigProperty(eq(TestConfigProperties.EXAMPLE_PROPERTY), eq("value"));

        // value in underlying config
        assertEquals("value",
            spy.getConfigPropertyOrError(TestConfigProperties.EXAMPLE_PROPERTY));
    }


    static class LocalHashMapConfigService implements ConfigService {

        Map<ConfigProperty, String> map = new HashMap<>();

        @Override
        public void putConfigProperty(ConfigProperty property, String value) {
            map.put(property, value);
        }

        @Override
        public String getConfigPropertyOrError(ConfigProperty property) {
            if (map.containsKey(property)) {
                return map.get(property);
            } else {
                throw new NoSuchElementException("Psoxy misconfigured. Expected value for: " + property.name());
            }
        }

        @Override
        public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
            return Optional.empty();
        }

        @Override
        public boolean supportsWriting() {
            return true;
        }
    }
}
