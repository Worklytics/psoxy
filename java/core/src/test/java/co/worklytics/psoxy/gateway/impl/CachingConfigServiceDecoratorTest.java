package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CachingConfigServiceDecoratorTest {


    InMemorySecretStore inMemorySecretStore;
    SecretStore config;

    @BeforeEach
    public void setup() {
        inMemorySecretStore = new InMemorySecretStore();
        config = new CachingSecretStoreDecorator(inMemorySecretStore, Duration.ofMinutes(1));
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
    void writeSecret() {
        assertTrue(config.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY).isEmpty());

        assertThrows(NoSuchElementException.class,
            () -> config.getConfigPropertyOrError(TestConfigProperties.EXAMPLE_PROPERTY));

        //read from underlying cache
        assertEquals(1, inMemorySecretStore.getReads());

        //negatively cached
        assertEquals(CachingConfigServiceDecorator.NEGATIVE_VALUE,
            ((CachingSecretStoreDecorator) config).getCache().get(TestConfigProperties.EXAMPLE_PROPERTY));

        config.writeSecret(TestConfigProperties.EXAMPLE_PROPERTY, "value");

        assertEquals("value", config.getConfigPropertyAsOptional(TestConfigProperties.EXAMPLE_PROPERTY).get());
        assertEquals("value", config.getConfigPropertyOrError(TestConfigProperties.EXAMPLE_PROPERTY));

        // still only called that first time
        assertEquals(1, inMemorySecretStore.getReads());

        // written exactly once
        assertEquals(1, inMemorySecretStore.getWrites());

        // value in underlying config
        assertEquals("value",
            inMemorySecretStore.getConfigPropertyOrError(TestConfigProperties.EXAMPLE_PROPERTY));
    }

    @Test
    void getConfigProperty_noCache() {
        assertTrue(config.getConfigPropertyAsOptional(TestConfigProperties.NO_CACHE).isEmpty());
        assertEquals(1, inMemorySecretStore.getReads());


        //after write, still goes to origin
        config.writeSecret(TestConfigProperties.NO_CACHE, "value");
        assertEquals("value",
            config.getConfigPropertyOrError(TestConfigProperties.NO_CACHE));
        assertEquals(2, inMemorySecretStore.getReads());

        // after read, still goes to origin
        assertEquals("value",
            config.getConfigPropertyOrError(TestConfigProperties.NO_CACHE));
        assertEquals(3, inMemorySecretStore.getReads());
    }


    static class InMemorySecretStore implements SecretStore {

        Map<ConfigProperty, String> map = new HashMap<>();

        @Getter
        int reads = 0;

        @Getter
        int writes = 0;

        @Override
        public void writeSecret(ConfigProperty property, String value) {
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

        @Override
        public List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit) {
            return Collections.emptyList();
        }
    }
}
