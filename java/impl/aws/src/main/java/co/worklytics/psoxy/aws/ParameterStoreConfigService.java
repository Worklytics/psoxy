package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * implementation of ConfigService backed by AWS Systems Manager Parameter Store
 *
 * https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html
 *
 */
//TODO: AssistedFactory??
//@NoArgsConstructor(onConstructor_ = @Inject)
// IDE accepts this, but mvn compile complains --> badly linked lombok??
//[ERROR] /Users/erik/code/psoxy/java/impl/aws/src/main/java/co/worklytics/psoxy/aws/ParameterStoreConfigService.java:[18,20] cannot find symbol
//[ERROR]   symbol:   method onConstructor_()
//[ERROR]   location: @interface lombok.NoArgsConstructo
@Log
@RequiredArgsConstructor
public class ParameterStoreConfigService implements ConfigService {

    final String namespace;

    final Duration defaultTtl;

    @Inject
    @NonNull
    SsmClient client;

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
                            public String load(String key) throws AwsServiceException {
                                try {
                                    GetParameterRequest parameterRequest = GetParameterRequest.builder()
                                    .name(key)
                                    .withDecryption(true)
                                    .build();
                                    GetParameterResponse parameterResponse = client.getParameter(parameterRequest);
                                    return parameterResponse.parameter().value();
                                } catch (SsmException ignore) {
                                    // does not exist, that could be OK depending on case.
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
            // first in the local cache so other threads get the most recent
            getCache().put(key, value);
            PutParameterRequest parameterRequest = PutParameterRequest.builder()
                .name(key)
                .value(value)
                // if property exists, which should always be created first, this flags needs to be set
                .overwrite(true)
                .build();
            PutParameterResponse parameterResponse = client.putParameter(parameterRequest);
            log.info(String.format("Property: %s, stored version %d", key, parameterResponse.version()));
        } catch (SsmException e) {
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
            if (cause instanceof AwsServiceException) {
                AwsServiceException ase = (AwsServiceException) cause;
                if (ase.isThrottlingException()) {
                    log.log(Level.SEVERE, String.format("Throttling issues for key %s, rate limit reached most likely despite retries", paramName), ase);
                }
            }
            throw new IllegalStateException(String.format("failed to get config value: %s", paramName), cause);
        } catch (UncheckedExecutionException uee) {
            // unchecked?
            throw new IllegalStateException(String.format("failed to get config value: %s", paramName), uee.getCause());
        }
    }

}
