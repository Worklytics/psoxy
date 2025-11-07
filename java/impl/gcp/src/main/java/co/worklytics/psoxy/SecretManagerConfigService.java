package co.worklytics.psoxy;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.DestroySecretVersionRequest;
import com.google.cloud.secretmanager.v1.ListSecretVersionsRequest;
import com.google.cloud.secretmanager.v1.Secret;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretName;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersion;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.google.cloud.secretmanager.v1.UpdateSecretRequest;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import com.google.protobuf.FieldMask;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.LockService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.WritableConfigService;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

@Log
public class SecretManagerConfigService implements WritableConfigService, LockService, SecretStore {

    private static final String LOCK_LABEL = "locked";
    private static final String VERSION_LABEL = "latest-version";
    private static final int MIN_NUMBER_OF_VERSIONS_TO_RETRIEVE = 20;

    /**
     *  GCP-level alias for the latest version of the secret
     *
     *  NOTE: accessing secrets via aliases, including 'latest', is NOT strongly consistent
     *  see: https://cloud.google.com/secret-manager/docs/access-secret-version#resource_consistency
     */
    public static final String LATEST_VERSION_ALIAS = "latest";

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
    public void putConfigProperty(ConfigProperty property, String value) {
        Preconditions.checkArgument(!property.isEnvVarOnly(), "Can't put env-only config property: " + property);

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

                destroyOldSecretVersions(client, secretName, version);
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
        if (property.isEnvVarOnly()) {
            Optional.empty();
        }


        String paramName = parameterName(property);

        SecretName secretName = SecretName.of(projectId, paramName);

        boolean accessViaExplicitVersion = false;

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {

            String versionName = LATEST_VERSION_ALIAS;
            try {
                Secret secret = client.getSecret(secretName);

                String versionLabelValue = secret.getLabelsMap() != null ? secret.getLabelsMap().get(VERSION_LABEL) : null;

                if (!StringUtils.isBlank(versionLabelValue)) {
                    accessViaExplicitVersion = true;
                    log.info("Accessing secret " + paramName + " using explicit version " + versionLabelValue + " rather than 'latest'.");
                    versionName = versionLabelValue;
                }
            } catch (PermissionDeniedException e) {
                // can happen in read-only case, where Cloud Function's SA has only Secret Manager Secret Accessor role
                // see: https://cloud.google.com/secret-manager/docs/access-control#secretmanager.secretAccessor
                if (envVarsConfigService.isDevelopment()) {
                    log.log(Level.INFO, "PermissionDeniedException getting secret " + paramName + "; will try to get 'latest' version directly");
                }
            }

            SecretVersionName secretVersionName =
                    SecretVersionName.of(projectId, secretName.getSecret(), versionName);

            return accessSecretVersion(client, secretVersionName);

        } catch (com.google.api.gax.rpc.NotFoundException e) {
            if (accessViaExplicitVersion) {
                // in this case, manual rotation of the secret without relying on our code may have occurred; a new version was written, but the value of the label referencing the latest version was not updated
                // log that we're here, then try to failover to getting the latest version using the GCP Secret Manager 'latest' reference.
                log.log(Level.WARNING, "Could not find secret " + paramName + " in Secret Manager using explicit version; check value of label '" + VERSION_LABEL + "' on the secret itself vs what versions of that secret exist.");

                try (SecretManagerServiceClient client = SecretManagerServiceClient.create())  {
                    // try to get the latest version
                    SecretVersionName secretVersionName =
                            SecretVersionName.of(projectId, secretName.getSecret(), LATEST_VERSION_ALIAS);
                    return accessSecretVersion(client, secretVersionName);
                } catch (com.google.api.gax.rpc.NotFoundException notFoundException) {
                    log.log(Level.WARNING, "Failover to getting 'latest' version of secret " + paramName + " also failed; check if secret exists and has versions.", notFoundException);
                } catch (Exception ignored) {
                    log.log(Level.WARNING, "Failover to getting 'latest' version of secret " + paramName + " failed due to something other than 'NotFound' case.", ignored);
                }
            }

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

    Optional<String> accessSecretVersion(SecretManagerServiceClient client, SecretVersionName secretVersionName) {
        // Access the secret version.
        AccessSecretVersionResponse response = client.accessSecretVersion(secretVersionName);

        return Optional.of(response.getPayload().getData().toStringUtf8());
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
                // It should not happen, as the secret should have been created by Terraform
                throw new RuntimeException(String.format("Secret %s does not exist, but should have be created by Terraform", lockSecretName));
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
            throw e;
        }
    }

    private static void destroyOldSecretVersions(SecretManagerServiceClient client, SecretName secretName, SecretVersion exceptVersion) {

        // 1. destroy up to ONE page of ENABLED secret versions (max 20)
        try {
            SecretManagerServiceClient.ListSecretVersionsPage enabledVersions =
                    client.listSecretVersions(ListSecretVersionsRequest.newBuilder()
                                    .setFilter("state:ENABLED")
                                    .setParent(secretName.toString())
                                    // Reduce the page, as each version will be disabled one by one
                                    .setPageSize(MIN_NUMBER_OF_VERSIONS_TO_RETRIEVE)
                                    .build())
                            .getPage();

            enabledVersions.getValues().forEach(i -> {
                if (!i.getName().equals(exceptVersion.getName())) {
                    client.destroySecretVersion(DestroySecretVersionRequest.newBuilder()
                            .setName(i.getName())
                            .build());

                    log.info(String.format("Property: %s, destroyed version %s", secretName, i.getName()));
                }
            });
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Failed to destroy old versions of secret %s", secretName), e);
            throw e;
        }

        // 2. transitionally, destroy up to ONE page of old, DISABLED secret versions
        // transitional - added in 0.4.49; previously, we just DISABLED secret versions, but GCP
        // still bills for these - "0.06 per version per location per month for 'Active secret versions'"
        // in Notes section of their pricing paging,
        // "1. A secret version is 'active' when it's in any of these states: ENABLED, DISABLED"
        // so we need to DESTROY any versions of these secrets that are merely DISABLED
        try {
            Instant until = Instant.ofEpochSecond(exceptVersion.getCreateTime().getSeconds())
                    .minus(Duration.ofDays(7));

            SecretManagerServiceClient.ListSecretVersionsPage disabledVersions =
                    client.listSecretVersions(ListSecretVersionsRequest.newBuilder()
                                    // From docs, https://cloud.google.com/secret-manager/docs/filtering#filter
                                    // when comparing time we need to use RFC3399 (2020-10-15T01:30:15Z)
                                    // but filter returns an invalid token error on that value when parsing the ":" of the date
                                    // Tried several ways but none working, so let's use
                                    // just the
                                    .setFilter("state:DISABLED AND create_time < " + DateTimeFormatter.ISO_LOCAL_DATE
                                            .withZone(ZoneOffset.UTC)
                                            .format(until))
                                    .setParent(secretName.toString())
                                    .setPageSize(MIN_NUMBER_OF_VERSIONS_TO_RETRIEVE)
                                    .build())
                            .getPage();

            //destroy all
            disabledVersions.getValues().forEach(i -> {
                client.destroySecretVersion(DestroySecretVersionRequest.newBuilder()
                        .setName(i.getName())
                        .build());
            });
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Failed to destroy old, disabled versions of secret %s", secretName), e);
            //suppress - we don't want to fail if can't clean up old versions
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

    @Override
    public List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit) {
        if (property.isEnvVarOnly()) {
            return Collections.emptyList();
        }

        String paramName = parameterName(property);
        SecretName secretName = SecretName.of(projectId, paramName);

        try (SecretManagerServiceClient client = SecretManagerServiceClient.create()) {
            // List enabled versions of the secret
            ListSecretVersionsRequest request = ListSecretVersionsRequest.newBuilder()
                .setParent(secretName.toString())
                .setFilter("state:ENABLED")
                .setPageSize(Math.max(limit, MIN_NUMBER_OF_VERSIONS_TO_RETRIEVE))
                .build();

            SecretManagerServiceClient.ListSecretVersionsPagedResponse response = client.listSecretVersions(request);

            // Convert to list and sort by version number descending
            List<SecretVersion> versions = StreamSupport.stream(response.iterateAll().spliterator(), false)
                .collect(Collectors.toList());

            if (versions.isEmpty()) {
                return Collections.emptyList();
            }

            return versions.stream()
                .sorted((v1, v2) -> {
                    // Sort by version number (extracted from name) descending
                    Integer ver1 = extractVersionNumber(v1.getName());
                    Integer ver2 = extractVersionNumber(v2.getName());
                    if (ver1 != null && ver2 != null) {
                        return ver2.compareTo(ver1);
                    }
                    // Fallback to creation time (compare by seconds)
                    long time1 = v1.getCreateTime().getSeconds();
                    long time2 = v2.getCreateTime().getSeconds();
                    return Long.compare(time2, time1);
                })
                .limit(limit)
                .map(version -> {
                    try {
                        SecretVersionName secretVersionName = SecretVersionName.parse(version.getName());
                        Optional<String> value = accessSecretVersion(client, secretVersionName);
                        
                        if (value.isPresent()) {
                            return ConfigService.ConfigValueVersion.builder()
                                .value(value.get())
                                .lastModifiedDate(Instant.ofEpochSecond(version.getCreateTime().getSeconds()))
                                .version(extractVersionNumber(version.getName()))
                                .build();
                        }
                        return null;
                    } catch (Exception e) {
                        log.log(Level.WARNING, "Failed to retrieve version " + version.getName() + " of secret " + paramName, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        } catch (com.google.api.gax.rpc.NotFoundException e) {
            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "Could not find secret " + paramName + " when listing versions", e);
            }
            return Collections.emptyList();
        } catch (PermissionDeniedException e) {
            if (envVarsConfigService.isDevelopment()) {
                log.log(Level.INFO, "PermissionDeniedException listing versions for secret " + paramName, e);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.log(Level.WARNING, "Unexpected error listing secret versions for " + paramName, e);
            return Collections.emptyList();
        }
    }

    /**
     * Extract version number from GCP Secret Manager version name.
     * Version names are in format: projects/{project}/secrets/{secret}/versions/{version}
     */
    private Integer extractVersionNumber(String versionName) {
        if (versionName == null) {
            return null;
        }
        try {
            SecretVersionName secretVersionName = SecretVersionName.parse(versionName);
            String versionStr = secretVersionName.getSecretVersion();
            // Try to parse as integer; if it fails (e.g., "latest"), return null
            return Integer.parseInt(versionStr);
        } catch (Exception e) {
            return null;
        }
    }
}
