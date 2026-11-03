package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.ConfigService;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
@Builder
public class AwsWifGoogleCloudProcessIdentityConfig {

    public static AwsWifGoogleCloudProcessIdentityConfig fromConfigService(ConfigService configService) {
        AwsWifGoogleCloudProcessIdentityConfigBuilder builder = AwsWifGoogleCloudProcessIdentityConfig.builder();
        configService.getConfigPropertyAsOptional(AwsWifGoogleCloudProcessIdentity.ConfigProperty.GCP_WIF_AUDIENCE)
            .ifPresent(builder::gcpWifAudience);
        configService.getConfigPropertyAsOptional(AwsWifGoogleCloudProcessIdentity.ConfigProperty.GCP_WIF_TOKEN_URL)
            .filter(StringUtils::isNotBlank)
            .ifPresent(builder::gcpWifTokenUrl);
        configService.getConfigPropertyAsOptional(
                AwsWifGoogleCloudProcessIdentity.ConfigProperty.GCP_WIF_SERVICE_ACCOUNT_IMPERSONATION_URL)
            .filter(StringUtils::isNotBlank)
            .ifPresent(builder::gcpWifServiceAccountImpersonationUrl);
        return builder.build();
    }

    String gcpWifAudience;
    String gcpWifTokenUrl;
    String gcpWifServiceAccountImpersonationUrl;

    public String getGcpWifAudienceOrError() {
        return Optional.ofNullable(gcpWifAudience).orElseThrow(() -> new IllegalStateException(
            "Missing required config: " + AwsWifGoogleCloudProcessIdentity.ConfigProperty.GCP_WIF_AUDIENCE));
    }

    public Optional<String> getGcpWifTokenUrl() {
        return Optional.ofNullable(gcpWifTokenUrl);
    }

    public Optional<String> getGcpWifServiceAccountImpersonationUrl() {
        return Optional.ofNullable(gcpWifServiceAccountImpersonationUrl);
    }
}
