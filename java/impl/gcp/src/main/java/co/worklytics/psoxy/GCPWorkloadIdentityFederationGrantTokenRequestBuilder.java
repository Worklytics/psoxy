package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.impl.oauth.WorkloadIdentityFederationGrantTokenRequestBuilder;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.http.client.utils.URIBuilder;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

/**
 * Based on <a href="https://learn.microsoft.com/en-us/azure/active-directory/develop/workload-identity-federation-create-trust-gcp?tabs=azure-cli%2Cjava#get-an-id-token-for-your-google-service-account">...</a>
 *
 * Implementation of Workload Identity Federation for GCP, getting an ID token
 * to be exposed as client assertion
 */
@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class GCPWorkloadIdentityFederationGrantTokenRequestBuilder extends WorkloadIdentityFederationGrantTokenRequestBuilder {

    @Override
    @SneakyThrows
    protected String getClientAssertion(String audience) {
        URIBuilder uriBuilder = new URIBuilder();
        // This URL is internal to GCP
        uriBuilder.setScheme("http");
        uriBuilder.setHost("metadata.google.internal");
        uriBuilder.setPath("computeMetadata/v1/instance/service-accounts/default/identity");
        uriBuilder.setParameter("audience", audience);

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