package co.worklytics.psoxy.gateway.impl.oauth;

import co.worklytics.psoxy.gateway.ConfigService;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class WorkloadIdentityFederationGrantConfig {

    public static WorkloadIdentityFederationGrantConfig fromConfigService(ConfigService configService) {
        WorkloadIdentityFederationGrantConfigBuilder builder = WorkloadIdentityFederationGrantConfig.builder();
        configService.getConfigPropertyAsOptional(WorkloadIdentityFederationGrantTokenRequestBuilder.ConfigProperty.TOKEN_SCOPE)
            .ifPresent(builder::tokenScope);
        return builder.build();
    }

    String tokenScope;

    public Optional<String> getTokenScope() {
        return Optional.ofNullable(tokenScope);
    }
}
