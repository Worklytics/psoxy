package co.worklytics.psoxy.aws;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import com.amazonaws.SdkClientException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Uninterruptibles;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.utils.RandomNumberGenerator;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.java.Log;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.DeleteParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterHistoryRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterHistoryResponse;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterAlreadyExistsException;
import software.amazon.awssdk.services.ssm.model.ParameterHistory;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.ParameterVersionNotFoundException;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;
import software.amazon.awssdk.services.ssm.model.PutParameterResponse;
import software.amazon.awssdk.services.ssm.model.SsmException;

/**
 * implementation of ConfigService backed by AWS Systems Manager Parameter Store
 *
 * https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html
 *
 */
@Log
public class ParameterStoreConfigService implements SecretStore, LockService {


    /**
     * placeholder value, since SSM parameters can't have 'null' value or something
     *
     * @see infra/modules/aws-ssm-secrets/main.tf:12
     */
    @VisibleForTesting
    static final String PLACEHOLDER_VALUE = "fill me";

    @Getter(onMethod_ = @VisibleForTesting)
    final String namespace;

    @Inject
    SsmClient client;

    @Inject
    EnvVarsConfigService envVarsConfig;

    @Inject
    RandomNumberGenerator randomNumberGenerator;

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
    public void putConfigProperty(ConfigProperty property, String value) {
        Preconditions.checkArgument(!property.isEnvVarOnly(), "Can't put env-only config property: " + property);

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
            throw e;
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
        if (property.isEnvVarOnly()) {
            return Optional.empty();
        }

        String paramName = parameterName(property);

        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name(paramName)
                .withDecryption(true)
                .build();
            GetParameterResponse parameterResponse = client.getParameter(parameterRequest);

            Optional<T> r;
            if (Objects.equals(parameterResponse.parameter().value(), PLACEHOLDER_VALUE)) {
                log.info("Found placeholder value for " + paramName + "; this is either a misconfiguration, or a value that proxy itself should later fill.");
                r = Optional.empty();
            } else {
                if (envVarsConfig.isDevelopment()) {
                    log.info("Found SSM parameter for " + paramName);
                }
                r = Optional.of(mapping.apply(parameterResponse));
            }
            return r;
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
        } catch (SdkClientException e) {
            // transient IO related errors reading from SSM? can't reproduce exactly
            log.log(Level.SEVERE, "Error reading configuration from SSM: " + e.getMessage(), e);
            throw e;
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
        // TODO: in v0.6 i think this would be better as `_lock` suffix - or drop it entirely?? (just use the lockId??)
        return this.namespace + "lock_" + lockId;
    }

    @VisibleForTesting
    String lockParameterValue() {
        Instant instant = clock.instant();
        return String.format("locked_%d", instant.toEpochMilli());
    }

    @Override
    public boolean acquire(@NonNull String lockId, @NonNull Duration expires) {
        Preconditions.checkArgument(StringUtils.isNotBlank(lockId), "lockId must be non-blank");

        final String lockParameterName = lockParameterName(lockId);
        try {
            String lockValue = lockParameterValue();
            PutParameterResponse locked = client.putParameter(PutParameterRequest.builder()
                .name(lockParameterName)
                .type(ParameterType.STRING)
                .value(lockValue)
                .overwrite(false)
                .build());
            // assuming two threads at the same time trying to write on a deleted parameter might
            // not get ParameterAlreadyExistsException and last one wins.
            Uninterruptibles.sleepUninterruptibly(Duration.ofMillis(100).plusMillis(randomNumberGenerator.nextInt(200)));
            // if value doesn't match what we wrote, someone else got it, race condition.
            Parameter parameter = readParameter(lockParameterName);
            return Objects.equals(lockValue, parameter.value());
        } catch (ParameterAlreadyExistsException e) {
            try {
                Instant lockedAt = readParameter(lockParameterName).lastModifiedDate();

                if (lockedAt.isBefore(clock.instant().minusSeconds(expires.getSeconds()))) {
                    log.warning("Lock " + lockParameterName + " is stale, removing");

                    // release it and let the caller decide if the retry
                    release(lockId);
                }
            } catch (SsmException ssmException) {
                log.log(Level.SEVERE, "Could not read lock " + lockParameterName, ssmException);
            }
        } catch (SsmException e) {
            log.log(Level.SEVERE, "Could not acquire lock " + lockParameterName, e);
        }
        return false;
    }

    private Parameter readParameter(String parameterName) {
        GetParameterRequest parameterRequest = GetParameterRequest.builder()
            .name(parameterName)
            .build();
        GetParameterResponse parameterResponse = client.getParameter(parameterRequest);
        return parameterResponse.parameter();
    }


    @Override
    public void release(@NonNull String lockId) {
        Preconditions.checkArgument(StringUtils.isNotBlank(lockId), "lockId must be non-blank");

        String lockParameterName = lockParameterName(lockId);
        try {
            client.deleteParameter(DeleteParameterRequest.builder()
                .name(lockParameterName)
                .build());
        } catch (ParameterNotFoundException e) {
            log.log(Level.WARNING, "Lock " + lockParameterName + " not found; OK, but may indicate a problem", e);
        } catch (SsmException e) {
            // should go stale in this case ...
            log.log(Level.SEVERE, "Could not release lock " + lockParameterName, e);
        }
    }

    @Override
    public List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit) {
        if (property.isEnvVarOnly()) {
            return Collections.emptyList();
        }

        String paramName = parameterName(property);
        try {
            // Get parameter history to retrieve all versions
            GetParameterHistoryRequest request = GetParameterHistoryRequest.builder()
                .name(paramName)
                .withDecryption(true)
                .build();

            GetParameterHistoryResponse response = client.getParameterHistory(request);
            List<ParameterHistory> history = response.parameters();

            if (history == null || history.isEmpty()) {
                return Collections.emptyList();
            }

            // Sort by version descending and convert to ConfigValueVersion
            return history.stream()
                .filter(p -> !Objects.equals(p.value(), PLACEHOLDER_VALUE))
                .sorted((p1, p2) -> Long.compare(p2.version(), p1.version()))
                .limit(limit)
                .map((ParameterHistory paramHistory) -> ConfigService.ConfigValueVersion.builder()
                    .value(paramHistory.value())
                    .lastModifiedDate(paramHistory.lastModifiedDate())
                    .version(paramHistory.version().toString())
                    .build())
                .collect(Collectors.toList());

        } catch (ParameterNotFoundException | ParameterVersionNotFoundException e) {
            if (envVarsConfig.isDevelopment()) {
                log.info("No parameter history found for " + paramName + " when listing versions");
            }
            return Collections.emptyList();
        } catch (SsmException e) {
            if (envVarsConfig.isDevelopment()) {
                log.log(Level.WARNING, "failed to list parameter versions for " + paramName, e);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.log(Level.WARNING, "Unexpected error listing parameter versions for " + paramName, e);
            return Collections.emptyList();
        }
    }
}
