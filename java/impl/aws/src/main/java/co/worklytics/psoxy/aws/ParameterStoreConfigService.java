package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.ParameterVersionNotFoundException;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
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

    final Duration TTL;

    @Inject
    @NonNull
    SsmClient client;

    private volatile LoadingCache<String, String> cache;
    private final Object $writeLock = new Object[0];
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private LoadingCache<String, String> getCache() {
        if (this.cache == null) {
            synchronized ($writeLock) {
                if (this.cache == null) {
                    this.cache = CacheBuilder.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(TTL.getSeconds(), TimeUnit.SECONDS)
                        .recordStats()
                        .build(new CacheLoader<>() {
                            @Override
                            public String load(String key) throws AwsServiceException {
                                GetParameterRequest parameterRequest = GetParameterRequest.builder()
                                    .name(key)
                                    .withDecryption(true)
                                    .build();
                                GetParameterResponse parameterResponse = client.getParameter(parameterRequest);
                                return parameterResponse.parameter().value();
                            }
                        });
                    // https://github.com/google/guava/wiki/CachesExplained#when-does-cleanup-happen
                    // cache is mostly read, rare writes. We want this as much up-to-date as possible
                    // to avoid stale credentials
                    scheduler.schedule( () -> {
                        log.info(this.cache.stats().toString());
                        this.cache.cleanUp();
                    }, TTL.getSeconds(), TimeUnit.SECONDS);
                }
            }
        }
        return cache;
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
        try {
            return Optional.of(getCache().get(parameterName(property)));
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (ExceptionUtils.hasCause(cause, ParameterNotFoundException.class) ||
                ExceptionUtils.hasCause(cause, ParameterVersionNotFoundException.class)) {
                log.log(Level.WARNING, "Parameter not found", cause);
                return Optional.empty();
            }
            if (cause instanceof AwsServiceException) {
                AwsServiceException ase = (AwsServiceException) cause;
                if (ase.isThrottlingException()) {
                    log.log(Level.SEVERE, "throttling issues, rate limit reached most likely despite retries", ase);
                }
            }
            throw new IllegalStateException("failed to get config value: " + cause.getMessage(), cause);
        }
    }

}
