package co.worklytics.psoxy.gateway.impl;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Utils;
import co.worklytics.psoxy.gateway.ConfigService;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Process identity when this proxy is hosted on GCP (Cloud Functions, Cloud Run, or GCE).
 *
 * <p>Uses {@link ComputeEngineCredentials}: OAuth tokens for the service account <em>attached to
 * this instance</em>, fetched from the GCP metadata server. That is the identity the process
 * already runs as.
 *
 * <p>This is not Application Default Credentials. ADC on GCP often resolves to the same attached
 * SA, but can also load a JSON key from {@code GOOGLE_APPLICATION_CREDENTIALS} or user gcloud
 * ADC — which this class will not do.
 *
 * <p>No Psoxy config properties: the attached SA is an attribute of the GCP runtime (metadata
 * server), not something we configure. Only valid when the process actually has a metadata
 * server (it will fail on AWS, or on a laptop).
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class GcpHostedProcessIdentity implements GoogleCloudProcessIdentity {

    public static final String CONFIG_IDENTIFIER = "gcp_hosted";

    @Getter
    private final String configIdentifier = CONFIG_IDENTIFIER;

    /**
     * None — identity comes from the SA attached to this GCP-hosted process (see class javadoc).
     */
    @Getter(onMethod_ = @Override)
    private final Set<ConfigService.ConfigProperty> requiredConfigProperties = Collections.emptySet();

    @Getter(onMethod_ = @Override)
    private final Set<ConfigService.ConfigProperty> allConfigProperties = Collections.emptySet();

    @Inject
    HttpTransportFactory httpTransportFactory;

    @Override
    public GoogleCredentials getCredentials() {
        return ComputeEngineCredentials.newBuilder()
            .setHttpTransportFactory(httpTransportFactory)
            .setScopes(List.of(OAuth2Utils.CLOUD_PLATFORM_SCOPE))
            .build();
    }
}
