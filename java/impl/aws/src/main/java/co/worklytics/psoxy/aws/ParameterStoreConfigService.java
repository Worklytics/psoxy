package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.java.Log;
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
@AllArgsConstructor
public class ParameterStoreConfigService implements ConfigService {


    String namespace;

    @Inject SsmClient client;

    //TODO: add caching?? guava?? apache commons??

    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return getConfigPropertyAsOptional(property)
            .orElseThrow(() -> new Error("Proxy misconfigured; no value for " + property));
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name(String.join("_", this.namespace, property.name()))
                .withDecryption(true)
                .build();
            GetParameterResponse parameterResponse = client.getParameter(parameterRequest);
            return Optional.of(parameterResponse.parameter().value());
        } catch (ParameterNotFoundException | ParameterVersionNotFoundException e) {
            return Optional.empty();
        } catch (SsmException e) {
            log.log(Level.SEVERE, "failed to get config value", e);
            throw new IllegalStateException("failed to get config value: " + e.getMessage(), e);
        }
    }
}
