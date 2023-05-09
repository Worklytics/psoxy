package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.cloud.secretmanager.v1.*;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.FieldMask;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;

@Log
public class SecretManagerConfigService implements ConfigService, LockService {

    static final String LOCK_LABEL = "locked";

    @Inject
    EnvVarsConfigService envVarsConfigService;
    @Inject
    Clock clock;

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

    @AssistedInject
    public SecretManagerConfigService(@Assisted("projectId") @NonNull String projectId,
                                      @Assisted("namespace") @NonNull String namespace) {
        this.projectId = projectId;
        this.namespace = namespace;
    }

    @Override
    public boolean supportsWriting() {
        return true;
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        String key = parameterName(property);
        SecretName secretName = SecretName.of(projectId, key);
        try {
            try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
                SecretPayload payload =
                        SecretPayload.newBuilder()
                                .setData(ByteString.copyFrom(value.getBytes()))
                                .build();

                // Add the secret version.
                SecretVersion version = client.addSecretVersion(secretName, payload);

                log.info(String.format("Property: %s, stored version %s", secretName, version.getName()));

                // q: invalidate previous versions?
            }
        } catch (IOException e) {
            log.log(Level.SEVERE, "Could not store property " + secretName, e);
        }
    }

    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return getConfigPropertyAsOptional(property)
                .orElseThrow(() -> new NoSuchElementException("Proxy misconfigured; no value for " + property));
    }

    @SneakyThrows
    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        String paramName = parameterName(property);
        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            SecretVersionName secretVersionName = SecretVersionName.of(projectId, paramName, "latest");

            // Access the secret version.
            AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);

            return Optional.of(response.getPayload().getData().toStringUtf8());
        } catch (com.google.api.gax.rpc.NotFoundException e) {
            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "Could not find secret " + paramName + " in Secret Manager", e);
            }
            return Optional.empty();
        } catch (Exception ignored) {
            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "Some exception other than NotFoundException for " + paramName + " in Secret Manager", ignored);
            }
            // If secret is not found, it will return an exception
            return Optional.empty();
        }
    }

    @Override
    public boolean acquire(@NonNull String lockId, @NonNull java.time.Duration expires) {
        Preconditions.checkArgument(StringUtils.isNotBlank(lockId), "lockId cannot be blank");

        final SecretName lockSecretName = getLockSecret(lockId);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            Secret lockSecret;
            try {
                lockSecret = client.getSecret(lockSecretName);
            } catch (com.google.api.gax.rpc.NotFoundException e) {
                // It should not happen, as the secret has been created by Terraform
                throw new RuntimeException(e);
            }

            Instant lockedAt = Optional.ofNullable(lockSecret.getLabelsMap().get(LOCK_LABEL))
                    .map(i -> Instant.ofEpochMilli(Long.parseLong(i)))
                    .orElse(Instant.MIN);

            if (lockedAt.isBefore(clock.instant().minusSeconds(expires.getSeconds()))) {
                log.warning("Lock " + lockId + " is stale or unset; will try to acquire it");

                Secret updated = client.updateSecret(UpdateSecretRequest.newBuilder()
                        .setSecret(Secret.newBuilder(lockSecret)
                                // Label format is https://cloud.google.com/compute/docs/labeling-resources#requirements
                                // which an ISO-8601 cannot be added there (just like 2023-01-01T23:50:00Z)
                                // Instead, just using epoch in millis
                                .putLabels(LOCK_LABEL, Long.toString(Instant.now(clock).toEpochMilli()))
                                .build())
                        .setUpdateMask(FieldMask.newBuilder()
                                .addPaths("labels")
                                .build())
                        .build());
                //due to etag, update should have FAILED if was modified since our read
                return true;
            } else {
                //lock held by another processed
                return false;
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not create secret " + lockId, e);
            return false;
        }
    }

    @Override
    public void release(String lockId) {
        final SecretName lockSecretName = getLockSecret(lockId);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            Secret lockSecret = client.getSecret(lockSecretName);
            Secret updated = client.updateSecret(UpdateSecretRequest.newBuilder()
                    .setSecret(Secret.newBuilder(lockSecret)
                            .removeLabels(LOCK_LABEL)
                            .build())
                    .setUpdateMask(FieldMask.newBuilder()
                            .addPaths("labels")
                            .build())
                    .build());
            // again, due to etag this should FAIL if another processed locked in the meantime or
            // something
        } catch (Exception e) {
            log.log(Level.SEVERE, "Could not release lock " + lockId, e);
        }
    }

    private SecretName getLockSecret(String lockName) {
        // As lockName is handled by Terraform, variable should be converted to uppercase
        // otherwise secret will not be found
        return SecretName.of(projectId, this.namespace + lockName.toUpperCase());
    }

    private String parameterName(ConfigProperty property) {
        if (StringUtils.isBlank(this.namespace)) {
            return property.name();
        } else {
            return this.namespace + property.name();
        }
    }
}