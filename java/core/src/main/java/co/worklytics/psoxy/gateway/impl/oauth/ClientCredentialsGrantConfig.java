package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ClientCredentialsGrantConfig {

    public static ClientCredentialsGrantConfig fromConfigService(ConfigService configService) {
        ClientCredentialsGrantConfigBuilder builder = ClientCredentialsGrantConfig.builder();
        configService.getConfigPropertyAsOptional(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.TOKEN_SCOPE)
            .ifPresent(builder::tokenScope);
        configService.getConfigPropertyAsOptional(ClientCredentialsGrantTokenRequestBuilder.ConfigProperty.CREDENTIALS_FLOW)
            .flatMap(ClientCredentialsGrantTokenRequestBuilder.CredentialFlowType::parse)
            .ifPresent(builder::credentialsFlow);
        return builder.build();
    }

    String tokenScope;
    ClientCredentialsGrantTokenRequestBuilder.CredentialFlowType credentialsFlow;

    public Optional<String> getTokenScope() {
        return Optional.ofNullable(tokenScope);
    }

    public Optional<ClientCredentialsGrantTokenRequestBuilder.CredentialFlowType> getCredentialsFlow() {
        return Optional.ofNullable(credentialsFlow);
    }
}
