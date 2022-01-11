package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import software.amazon.awssdk.services.ssm.SsmClient;

import software.amazon.awssdk.services.ssm.model.*;

import javax.inject.Inject;
import java.util.Optional;

/**
 * implementation of ConfigService backed by AWS Systems Manager Parameter Store
 *
 * https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html
 *
 */
public class ParameterStoreConfigService implements ConfigService {

    @Inject SsmClient client;

    @Override
    public String getConfigPropertyOrError(ConfigProperty property) {
        return getConfigPropertyAsOptional(property)
            .orElseThrow(() -> new Error("Proxy misconfigured; no value for " + property));
    }

    @Override
    public Optional<String> getConfigPropertyAsOptional(ConfigProperty property) {
        try {
            GetParameterRequest parameterRequest = GetParameterRequest.builder()
                .name(property.name())
                .build();
            GetParameterResponse parameterResponse = client.getParameter(parameterRequest);
            return Optional.of(parameterResponse.parameter().value());
        } catch (ParameterNotFoundException | ParameterVersionNotFoundException e) {
            return Optional.empty();
        } catch (SsmException e) {
            throw new IllegalStateException("failed to get config value", e);
        }
    }

    @Override
    public boolean isDevelopment() {
        return this.getConfigPropertyAsOptional(ProxyConfigProperty.IS_DEVELOPMENT_MODE)
            .map(Boolean::parseBoolean).orElse(false);
    }
}
