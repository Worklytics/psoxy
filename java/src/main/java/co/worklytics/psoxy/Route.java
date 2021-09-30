package co.worklytics.psoxy;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import lombok.SneakyThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;


public class Route implements HttpFunction {


    /**
     * expect ONE cloud function per data connection; so connection-level settings are encoded as
     * environment variables
     *
     * see "https://cloud.google.com/functions/docs/configuring/env-var"
     *
     * in production, these should NOT include secrets, which should be stored/accessed via SecretManager
     *
     * see "https://cloud.google.com/functions/docs/configuring/secrets"
     *
     *
     */
    enum ConfigProperty {

        //base64-encoded service account key, in lieu of application default; not for production-use!!
        PSOXY_DEV_SERVICE_ACCOUNT_KEY,

        //target API endpoint to forward request to
        TARGET_HOST,
    }

    private HttpRequestFactory requestFactory;

    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {

        //is there a lifecycle to initialize request factory??
        if (requestFactory == null) {
            requestFactory = getRequestFactory();
        }

        // re-write host
        //TODO: switch on method to support HEAD, etc
        com.google.api.client.http.HttpRequest sourceApiRequest =
            requestFactory.buildGetRequest(buildTarget(request));

        //TODO: what headers to forward???

        com.google.api.client.http.HttpResponse sourceApiResponse = sourceApiRequest.execute();

        // return response
        response.setStatusCode(sourceApiResponse.getStatusCode());
        sourceApiResponse.getContent().transferTo(response.getOutputStream());
    }

    GenericUrl buildTarget(HttpRequest request) {
        String targetUri = "https://"
            + getRequiredConfigProperty(ConfigProperty.TARGET_HOST)
            + request.getPath()
            + request.getQuery().map(s -> "?" + s).orElse("");

        return new GenericUrl(targetUri);
    }

    String getRequiredConfigProperty(ConfigProperty property) {
        String value = System.getenv(property.name());
        if (value == null) {
            throw new Error("Psoxy misconfigured. Expected value for: " + property.name());
        }
        return value;
    }

    Optional<String> getOptionalConfigProperty(ConfigProperty property) {
        return Optional.ofNullable(System.getenv(property.name()));
    }

    @SneakyThrows
    HttpRequestFactory getRequestFactory() {
        // per connection request factory, abstracts auth ..
        HttpTransport transport = new NetHttpTransport();

        //TODO: changing impl of credentials/initializer should support sources authenticated by
        // something OTHER than a Google Service account
        // eg, OAuth2CredentialsWithRefresh.newBuilder(), etc ..

        //assume that in cloud function env, this will get ours ...
        GoogleCredentials credentials =
            getOptionalConfigProperty(ConfigProperty.PSOXY_DEV_SERVICE_ACCOUNT_KEY)
                .map(encoded -> Base64.getDecoder().decode(encoded.getBytes(StandardCharsets.UTF_8)))
                .map(bytes -> new ByteArrayInputStream(bytes))
                .map(this::quietFromStream)
                .orElseGet(this::quietGetApplicationDefault);

        HttpCredentialsAdapter initializer = new HttpCredentialsAdapter(credentials);

        return transport.createRequestFactory(initializer);
    }

    /**
     * quiet helper to avoid try-catch
     *
     * @param s
     * @return
     */
    @SneakyThrows
    GoogleCredentials quietFromStream(InputStream s) {
        return ServiceAccountCredentials.fromStream(s);
    }

    /**
     *quiet helper to avoid try-catch
     *
     * @return
     */
    @SneakyThrows
    GoogleCredentials quietGetApplicationDefault() {
        return ServiceAccountCredentials.getApplicationDefault();
    }
}
