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
import com.google.common.util.concurrent.UncheckedExecutionException;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.TransientConfigException;
import co.worklytics.psoxy.gateway.WritableConfigService;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import co.worklytics.psoxy.utils.RandomNumberGeneratorImpl;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.util.logging.Level;


@Log
public class CachingConfigServiceDecorator implements WritableConfigService, SecretStore {

    static final int MAX_TRANSIENT_RETRIES = 3;
    static final long DEFAULT_TRANSIENT_RETRY_DELAY_MS = 500L;
    /** Upper bound on any single inter-attempt sleep during cache-miss retries (8 seconds). */
    static final long MAX_TRANSIENT_RETRY_DELAY_MS = 8_000L;

    final ConfigService delegate;
    final Duration defaultTtl;
    final Ticker ticker;
    final long transientRetryDelayMs;
    final RandomNumberGenerator randomNumberGenerator;

    public CachingConfigServiceDecorator(ConfigService delegate, Duration defaultTtl) {
        this(delegate, defaultTtl, Ticker.systemTicker(), DEFAULT_TRANSIENT_RETRY_DELAY_MS, new RandomNumberGeneratorImpl());
    }

    public CachingConfigServiceDecorator(ConfigService delegate, Duration defaultTtl,
                                         RandomNumberGenerator randomNumberGenerator) {
        this(delegate, defaultTtl, Ticker.systemTicker(), DEFAULT_TRANSIENT_RETRY_DELAY_MS, randomNumberGenerator);
    }

    @VisibleForTesting
    CachingConfigServiceDecorator(ConfigService delegate, Duration defaultTtl, Ticker ticker) {
        this(delegate, defaultTtl, ticker, DEFAULT_TRANSIENT_RETRY_DELAY_MS, new RandomNumberGeneratorImpl());
    }

    @VisibleForTesting
    CachingConfigServiceDecorator(ConfigService delegate, Duration defaultTtl, Ticker ticker, long transientRetryDelayMs) {
        this(delegate, defaultTtl, ticker, transientRetryDelayMs, new RandomNumberGeneratorImpl());
    }

    @VisibleForTesting
    CachingConfigServiceDecorator(ConfigService delegate, Duration defaultTtl, Ticker ticker,
                                  long transientRetryDelayMs, RandomNumberGenerator randomNumberGenerator) {
        this.delegate = delegate;
        this.defaultTtl = defaultTtl;
        this.ticker = ticker;
        this.transientRetryDelayMs = transientRetryDelayMs;
        this.randomNumberGenerator = randomNumberGenerator;
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
                                TransientConfigException lastException = null;
                                for (int attempt = 0; attempt < MAX_TRANSIENT_RETRIES; attempt++) {
                                    try {
                                        return delegate.getConfigPropertyAsOptional(key).orElse(NEGATIVE_VALUE);
                                    } catch (TransientConfigException e) {
                                        lastException = e;
                                        log.log(Level.WARNING, String.format("Transient failure on attempt %d/%d for config property %s",
                                            attempt + 1, MAX_TRANSIENT_RETRIES, key.name()));
                                    }
                                    if (attempt < MAX_TRANSIENT_RETRIES - 1) {
                                        sleepBeforeTransientRetry(key, attempt);
                                    }
                                }
                                throw lastException;
                            }

                            @Override
                            public ListenableFuture<String> reload(@NonNull ConfigProperty key, @NonNull String oldValue) {
                                try {
                                    String newValue = delegate.getConfigPropertyAsOptional(key).orElse(NEGATIVE_VALUE);
                                    // Fallback heuristic for backends that still swallow exceptions
                                    // (e.g. GCP SecretManagerConfigService): if the value was valid
                                    // before but now comes back empty, assume transient and retain.
                                    if (NEGATIVE_VALUE.equals(newValue) && !NEGATIVE_VALUE.equals(oldValue)) {
                                        log.log(Level.WARNING,
                                            String.format("Backend returned empty for config property %s which was previously set; assuming transient failure and retaining cached value",
                                            key.name()));
                                        return Futures.immediateFuture(oldValue);
                                    }
                                    return Futures.immediateFuture(newValue);
                                } catch (TransientConfigException e) {
                                    // Backend explicitly signalled a transient failure.
                                    // Returning the old value resets the write-time so Guava waits a
                                    // full TTL before retrying, rather than retrying on every request.
                                    log.log(Level.WARNING,
                                        String.format("Transient failure reloading config property %s; retaining cached value until next refresh cycle",
                                        key.name()));
                                    return Futures.immediateFuture(oldValue);
                                }
                            }
                        });
                }
            }
        }
        return cache;
    }

    /**
     * Full jitter in {@code [0, maxDelayMs]} before retrying after a transient cache miss.
     * {@code maxDelayMs} doubles with each failed attempt (500ms, 1s, 2s, … with default base)
     * until {@link #MAX_TRANSIENT_RETRY_DELAY_MS} (8 seconds).
     */
    @VisibleForTesting
    long computeTransientRetryDelayMs(int attemptZeroBased) {
        if (transientRetryDelayMs <= 0) {
            return 0;
        }
        long maxDelayMs = transientRetryDelayMs;
        for (int i = 0; i < attemptZeroBased; i++) {
            if (maxDelayMs >= MAX_TRANSIENT_RETRY_DELAY_MS) {
                maxDelayMs = MAX_TRANSIENT_RETRY_DELAY_MS;
                break;
            }
            maxDelayMs *= 2;
        }
        maxDelayMs = Math.min(MAX_TRANSIENT_RETRY_DELAY_MS, maxDelayMs);
        return randomNumberGenerator.nextInt((int) maxDelayMs + 1);
    }

    private void sleepBeforeTransientRetry(ConfigProperty key, int attemptZeroBased) {
        long delayMs = computeTransientRetryDelayMs(attemptZeroBased);
        if (delayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new TransientConfigException("Config load for " + key.name() + " interrupted during retry", ie);
        }
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
            } catch (UncheckedExecutionException e) {
                // Guava wraps RuntimeExceptions from load() in UncheckedExecutionException.
                // TransientConfigException is a RuntimeException, so it lands here.
                Throwable cause = e.getCause();
                if (cause instanceof TransientConfigException) {
                    // load() retried MAX_TRANSIENT_RETRIES times and still failed. Nothing was
                    // cached, so the next request will retry immediately. Re-throw so callers can
                    // distinguish a transient store outage from a genuinely missing property.
                    log.log(Level.WARNING,
                        "Transient backend failure for config property {0}; all retries exhausted",
                        property.name());
                    throw (TransientConfigException) cause;
                }
                throw (cause instanceof RuntimeException) ? (RuntimeException) cause : e;
            } catch (ExecutionException e) {
                // Guava wraps checked exceptions from load() in ExecutionException.
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
