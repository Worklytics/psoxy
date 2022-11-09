package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;


@RequiredArgsConstructor
public class CachingConfigServiceDecorator implements ConfigService {

    final ConfigService delegate;
    final Duration defaultTtl;

    private volatile LoadingCache<ConfigProperty, String> cache;

    private final Object $writeLock = new Object[0];

    private final String NEGATIVE_VALUE = "##NO_VALUE##";

    private LoadingCache<ConfigProperty, String> getCache() {
        if (this.cache == null) {
            synchronized ($writeLock) {
                if (this.cache == null) {
                    this.cache = CacheBuilder.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(defaultTtl.getSeconds(), TimeUnit.SECONDS)
                        .recordStats()
                        .build(new CacheLoader<>() {
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
    public boolean supportsWriting() {
        return delegate.supportsWriting();
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        getCache().put(property, value);
        delegate.putConfigProperty(property, value);
    }

    @SneakyThrows
    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return getCache().get(property);
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        return Optional.empty();
    }

    @Override
    public boolean isDevelopment() {
        return delegate.isDevelopment();
    }
}
