package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.WritableConfigService;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


@RequiredArgsConstructor
public class CachingConfigServiceDecorator implements WritableConfigService, SecretStore {

    final ConfigService delegate;
    final Duration defaultTtl;

    private volatile LoadingCache<ConfigProperty, String> cache;

    private final Object $writeLock = new Object[0];

    @VisibleForTesting
    static final String NEGATIVE_VALUE = "##NO_VALUE##";

    @VisibleForTesting
    LoadingCache<ConfigProperty, String> getCache() {
        if (this.cache == null) {
            synchronized ($writeLock) {
                if (this.cache == null) {
                    this.cache = CacheBuilder.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(defaultTtl.getSeconds(), TimeUnit.SECONDS)
                        .recordStats()
                        .build(new CacheLoader<ConfigProperty, String>() {  //req for java8-backwards compatibility
                            @Override
                            public String load(ConfigProperty key) {
                                return delegate.getConfigPropertyAsOptional(key).orElse(NEGATIVE_VALUE);
                            }
                        });
                }
            }
        }
        return cache;
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        if (delegate instanceof WritableConfigService) {
            if (!property.noCache()) {
                getCache().put(property, value);
            }
            ((WritableConfigService) delegate).putConfigProperty(property, value);
        } else {
            throw new UnsupportedOperationException("ConfigService does not support writes: " + delegate.getClass().getName());
        }
    }

    @SneakyThrows
    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return getConfigPropertyAsOptional(property)
            .orElseThrow(() -> new NoSuchElementException("Psoxy misconfigured. Expected value for: " + property.name()));
    }

    @SneakyThrows
    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        if (property.noCache()) {
            return delegate.getConfigPropertyAsOptional(property);
        } else {
            try {
                String value = getCache().get(property);
                if (Objects.equals(NEGATIVE_VALUE, value)) {
                    return Optional.empty();
                } else {
                    return Optional.of(value);
                }
            } catch (ExecutionException e) {
                //unwrap if possible, re-throw
                if (e.getCause() == null) {
                    throw e;
                } else {
                    throw e.getCause();
                }
            }
        }
    }

}
