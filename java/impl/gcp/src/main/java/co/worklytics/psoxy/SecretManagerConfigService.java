package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import com.google.cloud.secretmanager.v1.*;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.protobuf.ByteString;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

@Log
@RequiredArgsConstructor
public class SecretManagerConfigService implements ConfigService {

    /**
     * Namespace to use; it could be empty for accessing all the secrets or with some value will be used
     * for being the prefix of the key to use. Ex of a key using a namespace: someNamespace_myKey
     */
    final String namespace;

    /**
     * GCP projectId to use; it could be the name of the project (my-project) or its id (1234)
     */
    @NonNull
    final String projectId;

    /**
     * TTL to use for cache expiration
     */
    @NonNull
    final Duration defaultTtl;
    private volatile LoadingCache<String, String> cache;
    private final Object $writeLock = new Object[0];

    private final String NEGATIVE_VALUE = "##NO_VALUE##";

    private LoadingCache<String, String> getCache() {
        if (this.cache == null) {
            synchronized ($writeLock) {
                if (this.cache == null) {
                    this.cache = CacheBuilder.newBuilder()
                            .maximumSize(100)
                            .expireAfterWrite(defaultTtl.getSeconds(), TimeUnit.SECONDS)
                            .recordStats()
                            .build(new CacheLoader<>() {
                                @Override
                                public String load(String key) {
                                    try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                                        SecretVersionName secretVersionName = SecretVersionName.of(projectId, key, "latest");

                                        // Access the secret version.
                                        AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);

                                        return response.getPayload().getData().toStringUtf8();
                                    } catch (Exception ignored) {
                                        // If secret is not found, it will return an exception
                                        return NEGATIVE_VALUE;
                                    }
                                }
                            });
                }
            }
        }

        return cache;
    }

    @Override
    public boolean supportsWriting() {
        return true;
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        String key = parameterName(property);
        try {
            try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                // first in the local cache so other threads get the most recent
                getCache().put(key, value);

                SecretPayload payload =
                        SecretPayload.newBuilder()
                                .setData(ByteString.copyFrom(value.getBytes()))
                                .build();

                // Add the secret version.
                SecretVersion version = client.addSecretVersion(key, payload);

                log.info(String.format("Property: %s, stored version %s", key, version.getName()));
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not store property " + key, e);
        }
    }

    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return getConfigPropertyAsOptional(property)
                .orElseThrow(() -> new Error("Proxy misconfigured; no value for " + property));
    }

    private String parameterName(ConfigProperty property) {
        if (StringUtils.isBlank(this.namespace)) {
            return property.name();
        } else {
            return String.join("_", this.namespace, property.name());
        }
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        String paramName = null;
        try {
            paramName = parameterName(property);
            String value = getCache().get(paramName);
            // useful for debugging to check if cache works as expected
            // log.info(getCache().stats().toString());
            if (NEGATIVE_VALUE.equals(value)) {
                // Optional is common, do not log, just for testing/debugging purposes
                // log.log(Level.WARNING, String.format("Parameter not found %s", paramName));
                return Optional.empty();
            } else {
                return Optional.of(value);
            }
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            throw new IllegalStateException(String.format("failed to get config value: %s", paramName), cause);
        } catch (UncheckedExecutionException uee) {
            // unchecked?
            throw new IllegalStateException(String.format("failed to get config value: %s", paramName), uee.getCause());
        }
    }
}