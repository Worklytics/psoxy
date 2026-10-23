package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class GcpWorkloadIdentityFederationGrantConfig {

    public static GcpWorkloadIdentityFederationGrantConfig fromConfigService(ConfigService configService) {
        GcpWorkloadIdentityFederationGrantConfigBuilder builder = GcpWorkloadIdentityFederationGrantConfig.builder();
        configService.getConfigPropertyAsOptional(GCPWorkloadIdentityFederationGrantTokenRequestBuilder.ConfigProperty.AUDIENCE)
            .ifPresent(builder::audience);
        return builder.build();
    }

    String audience;

    public String getAudienceOrError() {
        return Optional.ofNullable(audience).orElseThrow(() -> new IllegalStateException("Missing WIF audience"));
    }
}
