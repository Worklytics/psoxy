package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules;
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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.URL;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Log
public class Route implements HttpFunction {

    //we have ~540 total in Cloud Function connection, so can have generous values here
    final int SOURCE_API_REQUEST_CONNECT_TIMEOUT = 30_000;
    final int SOURCE_API_REQUEST_READ_TIMEOUT = 300_000;

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
        SOURCE,
        //target API endpoint to forward request to
        TARGET_HOST,
        OAUTH_SCOPES,
        //this should ACTUALLY be stored in secret manager, and then exposed as env var to the
        // cloud function
        // see "https://cloud.google.com/functions/docs/configuring/secrets#gcloud"
        SERVICE_ACCOUNT_KEY,
        PSOXY_SALT,
    }

    /**
     * see "https://cloud.google.com/functions/docs/configuring/env-var"
     */
    enum RuntimeEnvironmentVariables {
        K_SERVICE,
    }

    /**
     * headers that control how Psoxy works
     *
     * anything passed as headers like this shouldn't have info-sec implications.
     */
    @RequiredArgsConstructor
    enum ControlHeader {
        //TODO: change this; it's a confusing misnomer in the ServiceAccountCredentials interface
        // ServiceAccountCredentials::createDelegated does exactly the same thing, and is more correct
        // - the service account is impersonated the user. (user's Google Workspace tenant has
        // made a domain-wide delegation grant to the service)
        USER_TO_IMPERSONATE("User-To-Impersonate"),
        ;

        @NonNull
        final String httpNamePart;

        public String getHttpHeader() {
            return "X-Psoxy-" + httpNamePart;
        }
    }

    Sanitizer sanitizer;

    void initSanitizer() {
        sanitizer = new SanitizerImpl(
            Sanitizer.Options.builder()
                .rules(PrebuiltSanitizerRules.MAP.get(getRequiredConfigProperty(ConfigProperty.SOURCE)))
                .pseudonymizationSalt(getOptionalConfigProperty(ConfigProperty.PSOXY_SALT).orElse("salt"))
                .build());
    }

    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {
        if (sanitizer == null) {
            initSanitizer();
        }

        //is there a lifecycle to initialize request factory??
        HttpRequestFactory requestFactory = getRequestFactory(request);
        //q: what about when request factory needs to set a service account user?

        // re-write host
        //TODO: switch on method to support HEAD, etc
        GenericUrl targetUrl = buildTarget(request);

        log.info("Proxy invoked with target: " + targetUrl.buildRelativeUrl());

        //TODO: test URL against blacklist regex??

        com.google.api.client.http.HttpRequest sourceApiRequest =
            requestFactory.buildGetRequest(targetUrl);

        //TODO: what headers to forward???

        //setup request
        sourceApiRequest
            .setThrowExceptionOnExecuteError(false)
            .setConnectTimeout(SOURCE_API_REQUEST_CONNECT_TIMEOUT)
            .setReadTimeout(SOURCE_API_REQUEST_READ_TIMEOUT);

        //q: add exception handlers for IOExceptions / HTTP error responses, so those retries
        // happen in proxy rather than on Worklytics-side?

        com.google.api.client.http.HttpResponse sourceApiResponse = sourceApiRequest.execute();


        // return response
        response.setStatusCode(sourceApiResponse.getStatusCode());

        String responseContent =
            new String(sourceApiResponse.getContent().readAllBytes(), sourceApiResponse.getContentCharset());
        if (isSuccessFamily(sourceApiResponse.getStatusCode())) {
            String pseudonymized = sanitizer.sanitize(targetUrl, responseContent);
            new ByteArrayInputStream(pseudonymized.getBytes(StandardCharsets.UTF_8))
                .transferTo(response.getOutputStream());
        } else {
            //write error, which shouldn't contain PII, directly
            log.log(Level.WARNING, "Source API Error " + responseContent);
            //TODO: could run this through DLP to be extra safe
            new ByteArrayInputStream(responseContent.getBytes(StandardCharsets.UTF_8))
                .transferTo(response.getOutputStream());
        }
    }

    boolean isSuccessFamily (int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }


    @SneakyThrows
    GenericUrl buildTarget(HttpRequest request) {
        String path =
            request.getPath()
                .replace(System.getenv(RuntimeEnvironmentVariables.K_SERVICE.name()) + "/", "")
            + request.getQuery().map(s -> "?" + s).orElse("");

        URL url = new URL("https", getRequiredConfigProperty(ConfigProperty.TARGET_HOST), path);

        return new GenericUrl(url.toString());
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

    Optional<List<String>> getHeader(HttpRequest request, ControlHeader header) {
        return Optional.ofNullable(request.getHeaders().get(header.getHttpHeader()));
    }



    @SneakyThrows
    HttpRequestFactory getRequestFactory(HttpRequest request) {
        // per connection request factory, abstracts auth ..
        HttpTransport transport = new NetHttpTransport();

        //TODO: changing impl of credentials/initializer should support sources authenticated by
        // something OTHER than a Google Service account
        // eg, OAuth2CredentialsWithRefresh.newBuilder(), etc ..

        //assume that in cloud function env, this will get ours ...
        Optional<String> accountToImpersonate =
            getHeader(request, ControlHeader.USER_TO_IMPERSONATE)
                .map(values -> values.stream().findFirst().orElseThrow());

        accountToImpersonate.ifPresentOrElse(
            user -> log.info("User to impersonate: " + user),
            () -> log.warning("we usually expect a user to impersonate"));

        GoogleCredentials credentials = quietGetApplicationDefault(accountToImpersonate,
            Arrays.stream(getRequiredConfigProperty(ConfigProperty.OAUTH_SCOPES).split(","))
                .collect(Collectors.toSet()));

        HttpCredentialsAdapter initializer = new HttpCredentialsAdapter(credentials);

        //TODO: in OAuth-type use cases, where execute() may have caused token to be refreshed, how
        // do we capture the new one?? ideally do this with listener/handler/trigger in Credential
        // itself, if that's possible


        return transport.createRequestFactory(initializer);
    }

    /**
     * quiet helper to avoid try-catch
     *
     * @return
     */
    @SneakyThrows
    GoogleCredentials quietGetApplicationDefault(Optional<String> serviceAccountUser, Set<String> scopes) {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

        if (!(credentials instanceof ServiceAccountCredentials)) {
            // only ServiceAccountCredentials (created from an actual service account key) support
            // domain-wide delegation
            // see examples - even when access is 'global', still need to impersonate a user
            // https://developers.google.com/admin-sdk/reports/v1/guides/delegation

            //NOTE: in practice SERVICE_ACCOUNT_KEY need not belong the to same service account
            // running the cloud function; but it could
            String key = getRequiredConfigProperty(ConfigProperty.SERVICE_ACCOUNT_KEY);
            credentials = ServiceAccountCredentials.fromStream(new ByteArrayInputStream(Base64.getDecoder().decode(key)));
        }

        if (serviceAccountUser.isPresent()) {
            //even though GoogleCredentials implements `createDelegated`, it's a no-op if the
            // credential type doesn't support it.
            credentials = credentials.createDelegated(serviceAccountUser.get());
        }

        credentials = credentials.createScoped(scopes);

        return credentials;
    }
}
