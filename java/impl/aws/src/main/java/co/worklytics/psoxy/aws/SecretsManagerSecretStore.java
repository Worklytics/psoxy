package co.worklytics.psoxy.aws;

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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.extern.java.Log;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.DecryptionFailureException;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsRequest;
import software.amazon.awssdk.services.secretsmanager.model.ListSecretVersionIdsResponse;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretVersionsListEntry;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * implementation of SecretStore backed by AWS Secrets Manager
 *
 * @see co.worklytics.psoxy.aws.ParameterStoreConfigService - model for this
 *
 */
@Log
public class SecretsManagerSecretStore implements SecretStore {

    @Getter(onMethod_ = @VisibleForTesting)
    final String namespace;

    @Inject
    SecretsManagerClient client;

    @Inject
    EnvVarsConfigService envVarsConfig;

    @AssistedInject
    SecretsManagerSecretStore(@Assisted String namespace) {
        //SSM parameter stores must be "fully qualified" if contain a "/"
        //q: is this true for Secrets Manager paths?
        if (StringUtils.isNotBlank(namespace) && namespace.contains("/") && !namespace.startsWith("/")) {
            namespace = "/" + namespace;
        }
        this.namespace = namespace;
    }

    @Override
    public void putConfigProperty(ConfigProperty property, String value) {
        Preconditions.checkArgument(!property.isEnvVarOnly(), "Can't put env-only config property: " + property);

        String id = secretId(property);
        try {
            PutSecretValueRequest request = PutSecretValueRequest.builder()
                .secretId(id)
                .secretString(value)
                .build();
            PutSecretValueResponse response = client.putSecretValue(request);

            log.info(String.format("Property: %s, stored version %d", id, response.versionId()));
        } catch (SecretsManagerException e) {
            log.log(Level.SEVERE, "failed to write secret", e);
            throw e;
        }
    }

    String secretId(ConfigProperty property) {
        if (StringUtils.isBlank(namespace)) {
            return property.name();
        } else {
            return namespace + property.name();
        }
    }

    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return getConfigPropertyAsOptional(property)
            //q: would it be better to have this throw the REAL error
            .orElseThrow(() -> new NoSuchElementException("Proxy misconfigured; no value for " + property));
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        return getConfigPropertyAsOptional(property, r -> r.secretString());
    }

    <T> Optional<T> getConfigPropertyAsOptional(ConfigProperty property, Function<GetSecretValueResponse, T> mapping) {
        if (property.isEnvVarOnly()) {
            return Optional.empty();
        }

        String id = secretId(property);
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(id)
                //specify version?
                .build();

            GetSecretValueResponse response = client.getSecretValue(request);
            return Optional.ofNullable(mapping.apply(response));
        } catch (DecryptionFailureException e ) {
            log.log(Level.SEVERE, "failed to read secret due to decryption error; check lambda's exec role perms for secret " + id);
            return Optional.empty();
        } catch (ResourceNotFoundException e) {
            // does not exist, that could be OK depending on case.
            if (envVarsConfig.isDevelopment()) {
                log.log(Level.INFO, "secret not found; may be expected; if not, check lambda's exec role perms for secret " + id);
            }
            return Optional.empty();
        } catch (SecretsManagerException e) {
            //permissions error hits this case ... could still be expected for optional secrets, as
            // explicit IAM grant made for each one that exists
            //eg
            // software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException:
            // User: arn:aws:sts::{{SOME_ACCOUNT_ID}}}:assumed-role/{{LAMBDAS_EXEC_ROLE}}/{{SESSION_NAME}} is not authorized to perform: secretsmanager:GetSecretValue on resource: {{SECRET_ID}} because no identity-based policy allows the secretsmanager:GetSecretValue action (Service: SecretsManager, Status Code: 400, Request ID: ---, Extended Request ID: null)
            if (envVarsConfig.isDevelopment()) {
                log.log(Level.WARNING, "failed to read secret " + id, e);
            }
            return Optional.empty();
        } catch (AwsServiceException e) {
            if (e.isThrottlingException()) {
                log.log(Level.SEVERE, String.format("Throttling issues for Secrets Manager Secret %s, rate limit reached most likely despite retries", id), e);
            }
            throw new IllegalStateException(String.format("failed to get config value: %s", id));
        }
    }

    @Override
    public List<ConfigService.ConfigValueVersion> getAvailableVersions(ConfigProperty property, int limit) {
        if (property.isEnvVarOnly()) {
            return Collections.emptyList();
        }

        String id = secretId(property);
        try {
            // List all versions of the secret
            ListSecretVersionIdsRequest request = ListSecretVersionIdsRequest.builder()
                .secretId(id)
                .build();

            ListSecretVersionIdsResponse response = client.listSecretVersionIds(request);

            // Get all versions and sort by creation date descending
            List<SecretVersionsListEntry> versions = response.versions();
            if (versions == null || versions.isEmpty()) {
                return Collections.emptyList();
            }

            // Filter to only include AWSCURRENT and AWSPREVIOUS versions
            // and sort by last accessed date (most recent first)
            return versions.stream()
                .filter(v -> v.versionStages() != null && 
                    (v.versionStages().contains("AWSCURRENT") || v.versionStages().contains("AWSPREVIOUS")))
                .sorted((v1, v2) -> {
                    if (v1.createdDate() != null && v2.createdDate() != null) {
                        return v2.createdDate().compareTo(v1.createdDate());
                    }
                    return 0;
                })
                .limit(limit)
                .map(version -> {
                    try {
                        // Retrieve the actual secret value for this version
                        GetSecretValueRequest getRequest = GetSecretValueRequest.builder()
                            .secretId(id)
                            .versionId(version.versionId())
                            .build();
                        GetSecretValueResponse getResponse = client.getSecretValue(getRequest);

                        return ConfigService.ConfigValueVersion.builder()
                            .value(getResponse.secretString())
                            .lastModifiedDate(version.createdDate() != null ? 
                                version.createdDate() : version.lastAccessedDate())
                            .version(version.versionId())
                            .build();
                    } catch (Exception e) {
                        log.log(Level.WARNING, "Failed to retrieve version " + version.versionId() + " of secret " + id, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        } catch (ResourceNotFoundException e) {
            if (envVarsConfig.isDevelopment()) {
                log.log(Level.INFO, "secret not found when listing versions; may be expected; secret " + id);
            }
            return Collections.emptyList();
        } catch (SecretsManagerException e) {
            if (envVarsConfig.isDevelopment()) {
                log.log(Level.WARNING, "failed to list secret versions for " + id, e);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.log(Level.WARNING, "Unexpected error listing secret versions for " + id, e);
            return Collections.emptyList();
        }
    }
}


