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
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import javax.inject.Inject;
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
public class ParameterStoreConfigService implements ConfigService {

    @Getter(onMethod_ = @VisibleForTesting)
    final String namespace;

    @Inject
    SsmClient client;

    @Inject
    EnvVarsConfigService envVarsConfig;

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
}
