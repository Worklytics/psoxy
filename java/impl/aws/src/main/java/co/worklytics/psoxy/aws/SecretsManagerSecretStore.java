package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import com.google.common.annotations.VisibleForTesting;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import lombok.Getter;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.*;

import javax.inject.Inject;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Function;
import java.util.logging.Level;

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
        if (property.isEnvVarOnly()) {
            throw new IllegalArgumentException("Can't put env-only config property: " + property);
        }
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
}
