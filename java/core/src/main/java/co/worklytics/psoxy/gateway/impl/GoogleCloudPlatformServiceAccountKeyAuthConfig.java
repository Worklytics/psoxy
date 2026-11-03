package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GoogleCloudPlatformServiceAccountKeyAuthConfig {

    public static GoogleCloudPlatformServiceAccountKeyAuthConfig fromConfigService(ConfigService configService) {
        GoogleCloudPlatformServiceAccountKeyAuthConfigBuilder builder =
            GoogleCloudPlatformServiceAccountKeyAuthConfig.builder();
        configService.getConfigPropertyAsOptional(
                GoogleCloudPlatformServiceAccountKeyAuthStrategy.ConfigProperty.OAUTH_SCOPES)
            .ifPresent(builder::oauthScopes);
        return builder.build();
    }

    String oauthScopes;

    public String getOauthScopesOrError() {
        return Optional.ofNullable(oauthScopes).orElseThrow(() -> new IllegalStateException(
            "Missing required config: "
                + GoogleCloudPlatformServiceAccountKeyAuthStrategy.ConfigProperty.OAUTH_SCOPES));
    }
}
