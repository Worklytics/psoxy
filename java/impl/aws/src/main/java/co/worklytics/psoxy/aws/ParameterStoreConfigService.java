package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import com.google.common.annotations.VisibleForTesting;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;

import javax.inject.Inject;
import java.util.Optional;
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

    @Getter(onMethod_ = @VisibleForTesting)
    final String namespace;

    @Inject
    @NonNull
    SsmClient client;


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
        String paramName = parameterName(property);

        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name(paramName)
                .withDecryption(true)
                .build();
            GetParameterResponse parameterResponse = client.getParameter(parameterRequest);
            return Optional.of(parameterResponse.parameter().value());
        } catch (ParameterNotFoundException | ParameterVersionNotFoundException ignore) {
            // does not exist, that could be OK depending on case.
            return Optional.empty();
        } catch (SsmException ignore) {
            // very likely the policy doesn't allow reading this parameter
            // OK in those cases
            return Optional.empty();
        } catch (AwsServiceException e) {
            if (e.isThrottlingException()) {
                log.log(Level.SEVERE, String.format("Throttling issues for key %s, rate limit reached most likely despite retries", paramName), e);
            }
            throw new IllegalStateException(String.format("failed to get config value: %s", paramName), e);
        }
    }

}
