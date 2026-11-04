package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GcpIamSignJwtAuthConfig {

    public static GcpIamSignJwtAuthConfig fromConfigService(ConfigService configService) {
        GcpIamSignJwtAuthConfigBuilder builder = GcpIamSignJwtAuthConfig.builder();
        configService.getConfigPropertyAsOptional(GcpIamSignJwtAuthStrategy.ConfigProperty.SERVICE_ACCOUNT_EMAIL)
            .ifPresent(builder::serviceAccountEmail);
        configService.getConfigPropertyAsOptional(GcpIamSignJwtAuthStrategy.ConfigProperty.OAUTH_SCOPES)
            .ifPresent(builder::oauthScopes);
        configService.getConfigPropertyAsOptional(GcpIamSignJwtAuthStrategy.ConfigProperty.PROCESS_IDENTITY_SOURCE)
            .ifPresent(builder::processIdentitySource);
        return builder.build();
    }

    String serviceAccountEmail;
    String oauthScopes;
    String processIdentitySource;

    public String getServiceAccountEmailOrError() {
        return Optional.ofNullable(serviceAccountEmail).orElseThrow(() -> new IllegalStateException(
            "Missing required config: " + GcpIamSignJwtAuthStrategy.ConfigProperty.SERVICE_ACCOUNT_EMAIL));
    }

    public String getOauthScopesOrError() {
        return Optional.ofNullable(oauthScopes).orElseThrow(() -> new IllegalStateException(
            "Missing required config: " + GcpIamSignJwtAuthStrategy.ConfigProperty.OAUTH_SCOPES));
    }

    public String getProcessIdentitySourceOrError() {
        return Optional.ofNullable(processIdentitySource).orElseThrow(() -> new IllegalStateException(
            "Missing required config: " + GcpIamSignJwtAuthStrategy.ConfigProperty.PROCESS_IDENTITY_SOURCE));
    }
}
