package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.common.annotations.VisibleForTesting;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import javax.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * implementation of ConfigService backed by AWS Systems Manager Parameter Store
 *
 * https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html
 *
 */
@Log
public class ParameterStoreConfigService implements ConfigService, LockService {

    @Getter(onMethod_ = @VisibleForTesting)
    final String namespace;

    @Inject
    SsmClient client;

    @Inject
    EnvVarsConfigService envVarsConfig;

    @Inject
    Clock clock;

    @AssistedInject
    ParameterStoreConfigService(@Assisted String namespace) {
        //SSM parameter stores must be "fully qualified" if contain a "/"
        if (StringUtils.isNotBlank(namespace) && namespace.contains("/") && !namespace.startsWith("/")) {
            namespace = "/" + namespace;
        }
        this.namespace = namespace;
    }


    @Override
    public boolean supportsWriting() {
        return true;
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        String key = parameterName(property);
        try {
            PutParameterRequest parameterRequest = PutParameterRequest.builder()
                //TODO: determine proper 'type' here based on ConfigProperty attribute?
                // (as of 2023-03, we don't update any params that *aren't* secrets, so not really issue)
                .type(ParameterType.SECURE_STRING) // in case parameter doesn't exist yet, SSM rejects PUT w/o type
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
            .orElseThrow(() -> new NoSuchElementException("Proxy misconfigured; no value for " + property));
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        return getConfigPropertyAsOptional(property, r -> r.parameter().value());
    }

    <T> Optional<T> getConfigPropertyAsOptional(ConfigProperty property, Function<GetParameterResponse, T> mapping) {
        String paramName = parameterName(property);

        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name(paramName)
                .withDecryption(true)
                .build();
            GetParameterResponse parameterResponse = client.getParameter(parameterRequest);

            if (envVarsConfig.isDevelopment()) {
                log.info("Found SSM parameter for " + paramName);
            }
            return Optional.of(mapping.apply(parameterResponse));
        } catch (ParameterNotFoundException | ParameterVersionNotFoundException ignore) {
            // does not exist, that could be OK depending on case.
            if (envVarsConfig.isDevelopment()) {
                log.info("No SSM parameter for " + paramName + " (may be expected)");
            }
            return Optional.empty();
        } catch (SsmException ignore) {
            // very likely the policy doesn't allow reading this parameter
            // may be OK in those cases
            if (envVarsConfig.isDevelopment()) {
                log.log(Level.WARNING, "Couldn't read SSM parameter for " + paramName, ignore);
            }
            return Optional.empty();
        } catch (AwsServiceException e) {
            if (e.isThrottlingException()) {
                log.log(Level.SEVERE, String.format("Throttling issues for key %s, rate limit reached most likely despite retries", paramName), e);
            }
            throw new IllegalStateException(String.format("failed to get config value: %s", paramName), e);
        }
    }

    @Override
    public Optional<ConfigValueWithMetadata> getConfigPropertyWithMetadata(ConfigProperty configProperty) {
        return getConfigPropertyAsOptional(configProperty, r -> ConfigValueWithMetadata.builder()
            .value(r.parameter().value())
            .lastModifiedDate(r.parameter().lastModifiedDate())
            .build());

    }

    @VisibleForTesting
    String parameterName(ConfigProperty property) {
        if (StringUtils.isBlank(this.namespace)) {
            return property.name();
        } else {
            return this.namespace + property.name();
        }
    }

    String lockParameterName(String lockId) {
        return this.namespace + "lock_" + lockId;
    }

    // make configurable? YAGNI, 2m should be plenty
    final int LOCK_TTL_SECONDS = 120;

    @Override
    public boolean acquire(@NonNull String lockId) {
        final String lockParameterName = lockParameterName(lockId);
        try {
            client.putParameter(PutParameterRequest.builder()
                .name(lockParameterName)
                .type(ParameterType.STRING)
                .value("locked")
                .overwrite(false)
                .build());
            return true;
        } catch (ParameterAlreadyExistsException e) {
            try {
                Instant lockedAt = client.getParameter(GetParameterRequest.builder()
                    .name(lockParameterName)
                    .build()).parameter().lastModifiedDate();

                if (lockedAt.isBefore(clock.instant().minusSeconds(LOCK_TTL_SECONDS))) {
                    log.warning("Lock " + lockParameterName + " is stale, removing");

                    //q: add random delay here, in case multiple instances have been waiting to
                    // acquire the lock?

                    release(lockId);
                    return acquire(lockId);
                }
            } catch (SsmException ssmException) {
                log.log(Level.SEVERE, "Could not read lock " + lockParameterName, ssmException);
            }
            return false;
        } catch (SsmException e) {
            log.log(Level.SEVERE, "Could not acquire lock " + lockParameterName, e);
            return false;
        }
    }

    @Override
    public void release(@NonNull String lockId) {
        client.deleteParameter(DeleteParameterRequest.builder()
            .name(this.namespace + lockId)
            .build());
    }
}
