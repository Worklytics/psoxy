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

    private static final String LOCK_LABEL = "locked";
    private static final String VERSION_LABEL = "latest-version";
    private static final int NUMBER_OF_VERSIONS_TO_RETRIEVE = 20;

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
                SecretVersionName secretVersionName = SecretVersionName.parse(version.getName());
                log.info(String.format("Property: %s, stored version %s", secretName, version.getName()));

                updateLabelFromSecret(client, secretName, VERSION_LABEL, secretVersionName.getSecretVersion());

                disableOldSecretVersions(client, secretName, version);
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
            SecretName secretName = SecretName.of(projectId, paramName);

            Secret secret = client.getSecret(secretName);

            String versionName = secret.getLabelsMap() != null ? secret.getLabelsMap().get(VERSION_LABEL) : null;

            if (StringUtils.isBlank(versionName)) {
                versionName = "latest";
            }

            SecretVersionName secretVersionName = SecretVersionName.of(projectId, secretName.getSecret(), versionName);

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
                throw new RuntimeException(String.format("Secret %s must exist as it should be created by Terraform", lockSecretName));
            }

            Instant lockedAt = Optional.ofNullable(lockSecret.getLabelsMap().get(LOCK_LABEL))
                    .map(i -> Instant.ofEpochMilli(Long.parseLong(i)))
                    .orElse(Instant.MIN);

            if (lockedAt.isBefore(clock.instant().minusSeconds(expires.getSeconds()))) {
                log.warning("Lock " + lockId + " is stale or unset; will try to acquire it");

                updateLabelFromSecret(client, lockSecretName, LOCK_LABEL, Long.toString(Instant.now(clock).toEpochMilli()));
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

    private static void updateLabelFromSecret(SecretManagerServiceClient client, SecretName secretName, String label, String labelValue) {
        try {
            client.updateSecret(UpdateSecretRequest.newBuilder()
                    .setSecret(Secret.newBuilder()
                            .setName(secretName.toString())
                            // Label format is https://cloud.google.com/compute/docs/labeling-resources#requirements
                            .putLabels(label, labelValue)
                            .build())
                    .setUpdateMask(FieldMask.newBuilder()
                            .addPaths("labels")
                            .build())
                    .build());
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Cannot put the label of the version on the secret %s", secretName.toString()), e);
        }
    }

    private static void disableOldSecretVersions(SecretManagerServiceClient client, SecretName secretName, SecretVersion exceptVersion) {
        try {
            SecretManagerServiceClient.ListSecretVersionsPage enabledVersions = client.listSecretVersions(ListSecretVersionsRequest.newBuilder()
                            .setFilter("state:ENABLED")
                            .setParent(secretName.toString())
                            // Reduce the page, as each version will be disabled one by one
                            .setPageSize(NUMBER_OF_VERSIONS_TO_RETRIEVE)
                            .build())
                    .getPage();

            // Disable secret version, just the first page
            enabledVersions.getValues().forEach(i -> {
                if (!i.getName().equals(exceptVersion.getName())) {
                    client.disableSecretVersion(DisableSecretVersionRequest.newBuilder()
                            .setName(i.getName())
                            .build());

                    log.info(String.format("Property: %s, disabled version %s", secretName, i.getName()));
                }
            });
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Cannot disable old versions from secret %s", secretName.toString()), e);
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