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
@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class GCPWorkloadIdentityFederationGrantTokenRequestBuilder extends WorkloadIdentityFederationGrantTokenRequestBuilder {

    enum ConfigProperty implements ConfigService.ConfigProperty {
        AUDIENCE,
        ;

        @Getter
        private boolean envVarOnly = true;
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
    @SneakyThrows
    protected String getClientAssertion() {
        URIBuilder uriBuilder = new URIBuilder();
        // This URL is internal to GCP
        uriBuilder.setScheme("http");
        uriBuilder.setHost("metadata.google.internal");
        uriBuilder.setPath("computeMetadata/v1/instance/service-accounts/default/identity");
        uriBuilder.setParameter("audience", getConfig().getConfigPropertyOrError(ConfigProperty.AUDIENCE));

        HttpURLConnection httpUrlConnection = (HttpURLConnection) uriBuilder.build().toURL().openConnection();
        httpUrlConnection.setRequestMethod("GET");
        httpUrlConnection.setRequestProperty("Metadata-Flavor", "Google ");

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
