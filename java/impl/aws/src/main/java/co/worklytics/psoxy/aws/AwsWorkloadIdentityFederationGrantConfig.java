package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.ConfigService;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AwsWorkloadIdentityFederationGrantConfig {

    public static AwsWorkloadIdentityFederationGrantConfig fromConfigService(ConfigService configService) {
        AwsWorkloadIdentityFederationGrantConfigBuilder builder = AwsWorkloadIdentityFederationGrantConfig.builder();
        configService.getConfigPropertyAsOptional(
                AWSWorkloadIdentityFederationGrantTokenRequestBuilder.ConfigProperty.IDENTITY_POOL_ID)
            .ifPresent(builder::identityPoolId);
        configService.getConfigPropertyAsOptional(
                AWSWorkloadIdentityFederationGrantTokenRequestBuilder.ConfigProperty.IDENTITY_ID)
            .ifPresent(builder::identityId);
        configService.getConfigPropertyAsOptional(
                AWSWorkloadIdentityFederationGrantTokenRequestBuilder.ConfigProperty.DEVELOPER_NAME_ID)
            .ifPresent(builder::developerNameId);
        return builder.build();
    }

    String identityPoolId;
    String identityId;
    String developerNameId;

    public String getIdentityPoolIdOrError() {
        return Optional.ofNullable(identityPoolId).orElseThrow(() -> new IllegalStateException("Missing IDENTITY_POOL_ID"));
    }

    public String getIdentityIdOrError() {
        return Optional.ofNullable(identityId).orElseThrow(() -> new IllegalStateException("Missing IDENTITY_ID"));
    }

    public String getDeveloperNameIdOrError() {
        return Optional.ofNullable(developerNameId).orElseThrow(() -> new IllegalStateException("Missing DEVELOPER_NAME_ID"));
    }
}
