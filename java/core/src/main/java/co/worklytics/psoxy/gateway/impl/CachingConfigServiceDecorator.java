package co.worklytics.psoxy.gateway.impl;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Ticker;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.WritableConfigService;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.jspecify.annotations.NonNull;

import java.util.logging.Level;


@Log
public class CachingConfigServiceDecorator implements WritableConfigService, SecretStore {

    final ConfigService delegate;
    final Duration defaultTtl;
    final Ticker ticker;

    public CachingConfigServiceDecorator(ConfigService delegate, Duration defaultTtl) {
        this(delegate, defaultTtl, Ticker.systemTicker());
    }

    @VisibleForTesting
    CachingConfigServiceDecorator(ConfigService delegate, Duration defaultTtl, Ticker ticker) {
        this.delegate = delegate;
        this.defaultTtl = defaultTtl;
        this.ticker = ticker;
    }

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
                        .ticker(ticker)
                        .refreshAfterWrite(defaultTtl.getSeconds(), TimeUnit.SECONDS)
                        .recordStats()
                        .build(new CacheLoader<ConfigProperty, String>() {  //req for java8-backwards compatibility
                            @Override
                            public String load(@NonNull ConfigProperty key) {
                                return delegate.getConfigPropertyAsOptional(key).orElse(NEGATIVE_VALUE);
                            }

                            @Override
                            public ListenableFuture<String> reload(@NonNull ConfigProperty key, @NonNull String oldValue) {
                                String newValue = delegate.getConfigPropertyAsOptional(key).orElse(NEGATIVE_VALUE);
                                // If the store returns empty but we previously had a valid value,
                                // treat it as a transient backend failure and retain the old value.
                                // Crucially, we return immediateFuture(oldValue) rather than a failed
                                // future: a failed future leaves the write-time unchanged so Guava
                                // would re-attempt the reload on every subsequent cache.get() call,
                                // hammering SSM for the entire outage window. Returning the old value
                                // marks the entry as freshly written, so the next retry waits a full TTL.
                                if (NEGATIVE_VALUE.equals(newValue) && !NEGATIVE_VALUE.equals(oldValue)) {
                                    log.log(Level.WARNING,
                                        "Transient failure reloading config property {0}; retaining cached value until next refresh cycle",
                                        key.name());
                                    return Futures.immediateFuture(oldValue);
                                }
                                return Futures.immediateFuture(newValue);
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

    @Override
    public List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit) {
        // Don't cache version lists, always delegate to underlying implementation
        if (delegate instanceof SecretStore) {
            return ((SecretStore) delegate).getAvailableVersions(property, limit);
        }
        return delegate.getConfigPropertyWithMetadata(property).map(value -> ConfigService.ConfigValueVersion.builder()
            .value(value.getValue())
            .lastModifiedDate(value.getLastModifiedDate().orElse(null))
            .version(null)
            .build()).stream().collect(Collectors.toList());
    }

}
