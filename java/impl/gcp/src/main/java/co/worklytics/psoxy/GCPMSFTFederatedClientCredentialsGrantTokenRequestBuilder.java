package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.impl.oauth.MSFTFederatedClientCredentialsGrantTokenRequestBuilder;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@NoArgsConstructor(onConstructor_ = @Inject)
public class GCPMSFTFederatedClientCredentialsGrantTokenRequestBuilder extends MSFTFederatedClientCredentialsGrantTokenRequestBuilder {

    public static final String ENDPOINT = "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience=api://AzureADTokenExchange";

    @Override
    @SneakyThrows
    protected String getClientAssertion() {
        // Based on
        // https://learn.microsoft.com/en-us/azure/active-directory/develop/workload-identity-federation-create-trust-gcp?tabs=azure-cli%2Cjava#get-an-id-token-for-your-google-service-account
        URL url = new URL(ENDPOINT);
        HttpURLConnection httpUrlConnection = (HttpURLConnection) url.openConnection();

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