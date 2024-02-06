package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
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
 * implementation of ConfigService backed by AWS Secrets Manager
 *
 * @see co.worklytics.psoxy.aws.ParameterStoreConfigService - model for this
 *
 */
@Log
public class SecretsManagerConfigService implements ConfigService {

    @Getter(onMethod_ = @VisibleForTesting)
    final String namespace;

    @Inject
    SecretsManagerClient client;

    @Inject
    EnvVarsConfigService envVarsConfig;

    @AssistedInject
    SecretsManagerConfigService(@Assisted String namespace) {
        //SSM parameter stores must be "fully qualified" if contain a "/"
        //q: is this true for Secrets Manager paths?
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
            .orElseThrow(() -> new NoSuchElementException("Proxy misconfigured; no value for " + property));
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        return getConfigPropertyAsOptional(property, r -> r.secretString());
    }

    <T> Optional<T> getConfigPropertyAsOptional(ConfigProperty property, Function<GetSecretValueResponse, T> mapping) {

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
            log.log(Level.SEVERE, "failed to read secret: " + id, e);
            return Optional.empty();
        } catch (AwsServiceException e) {
            if (e.isThrottlingException()) {
                log.log(Level.SEVERE, String.format("Throttling issues for Secrets Manager Secret %s, rate limit reached most likely despite retries", id), e);
            }
            throw new IllegalStateException(String.format("failed to get config value: %s", id));
        }
    }
}
