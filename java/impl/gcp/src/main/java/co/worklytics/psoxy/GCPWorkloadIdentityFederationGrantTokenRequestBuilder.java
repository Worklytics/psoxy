package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.impl.oauth.WorkloadIdentityFederationGrantTokenRequestBuilder;
import com.google.common.collect.Streams;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.http.client.utils.URIBuilder;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Based on <a href="https://learn.microsoft.com/en-us/azure/active-directory/develop/workload-identity-federation-create-trust-gcp?tabs=azure-cli%2Cjava#get-an-id-token-for-your-google-service-account">...</a>
 * <p>
 * Implementation of Workload Identity Federation for GCP, getting an ID token
 * to be exposed as client assertion
 */
@Singleton // respected??
@Log
public class GCPWorkloadIdentityFederationGrantTokenRequestBuilder extends WorkloadIdentityFederationGrantTokenRequestBuilder {


    @Inject
    public GCPWorkloadIdentityFederationGrantTokenRequestBuilder(ConfigService configService) {
        super(configService);
    }

    @Getter
    enum ConfigProperty implements ConfigService.ConfigProperty {
        AUDIENCE,
        ;

        @Override
        public SupportedSource getSupportedSource() {
            return SupportedSource.ENV_VAR;
        }
    }

    @Override
    public Set<ConfigService.ConfigProperty> getRequiredConfigProperties() {
        return Streams.concat(super.getRequiredConfigProperties().stream(),
                        Stream.of(ConfigProperty.values()))
                .collect(Collectors.toSet());
    }

    @Override
    public Set<ConfigService.ConfigProperty> getAllConfigProperties() {
        return Streams.concat(super.getAllConfigProperties().stream(),
                        Stream.of(ConfigProperty.values()))
                .collect(Collectors.toSet());
    }

    @Override
    protected void appendFederatedIdentityDebugSummary(StringBuilder summary) {
        getConfig().getConfigPropertyAsOptional(ConfigProperty.AUDIENCE)
            .ifPresent(v -> summary.append(", gcpIdTokenAudience=").append(v));
    }

    @Override
    @SneakyThrows
    protected String getClientAssertion() {
        String audience = getConfig().getConfigPropertyOrError(ConfigProperty.AUDIENCE);

        URIBuilder uriBuilder = new URIBuilder();
        // This URL is internal to GCP
        uriBuilder.setScheme("http");
        uriBuilder.setHost("metadata.google.internal");
        uriBuilder.setPath("computeMetadata/v1/instance/service-accounts/default/identity");
        uriBuilder.setParameter("audience", audience);

        log.info("Requesting GCP metadata identity token for MSFT federated credential: audience=" + audience);

        HttpURLConnection httpUrlConnection = (HttpURLConnection) uriBuilder.build().toURL().openConnection();
        httpUrlConnection.setRequestMethod("GET");
        httpUrlConnection.setRequestProperty("Metadata-Flavor", "Google ");

        int status = httpUrlConnection.getResponseCode();
        if (status < 200 || status >= 300) {
            String errorBody = "";
            try (InputStream errorStream = httpUrlConnection.getErrorStream()) {
                if (errorStream != null) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream))) {
                        errorBody = reader.lines().collect(Collectors.joining("\n"));
                    }
                }
            }
            log.severe("GCP metadata identity token request failed: status=" + status
                + ", audience=" + audience + ", body=" + errorBody);
            throw new java.io.IOException("GCP metadata identity token request failed: status="
                + status + " audience=" + audience + " body=" + errorBody);
        }

        StringBuilder content = new StringBuilder();

        try (InputStream inputStream = httpUrlConnection.getInputStream();
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {

            String inputLine;

            while ((inputLine = bufferedReader.readLine()) != null)
                content.append(inputLine);
        }

        return content.toString();
    }
}
