package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.SanitizerImpl;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpStatusCodes;
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
        SERVICE_ACCOUNT_USER("Service-Account-User"),
        ;

        @NonNull
        final String httpNamePart;

        public String getHttpHeader() {
            return "X-Psoxy-" + httpNamePart;
        }
    }

    Sanitizer sanitizer;

    void initSanitizer() {
        //TODO: pull salt from Secret Manager
        sanitizer = new SanitizerImpl(
            PrebuiltSanitizerOptions.MAP.get(getRequiredConfigProperty(ConfigProperty.SOURCE))
            .withPseudonymizationSalt("salt")
        );
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

        if (sourceApiResponse.getStatusCode() == HttpStatusCodes.STATUS_CODE_OK) {
            String json = new String(sourceApiResponse.getContent().readAllBytes(), sourceApiResponse.getContentCharset());
            String pseudonymized = sanitizer.sanitize(targetUrl, json);
            new ByteArrayInputStream(pseudonymized.getBytes(StandardCharsets.UTF_8))
                .transferTo(response.getOutputStream());
        } else {
            //write error, which shouldn't contain PII, directly
            //TODO: could run this through DLP to be extra safe
            sourceApiResponse.getContent().transferTo(response.getOutputStream());
        }
    }


    GenericUrl buildTarget(HttpRequest request) {
        String targetUri = "https://"
            + getRequiredConfigProperty(ConfigProperty.TARGET_HOST)
            + request.getPath().replace(System.getenv(RuntimeEnvironmentVariables.K_SERVICE.name()) + "/", "")
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

    Optional<List<String>> getHeader(HttpRequest request, ControlHeader header) {
        return Optional.ofNullable(request.getHeaders().get(header.getHttpHeader()));
    }

    Optional<String> getOptionalConfigProperty(ConfigProperty property) {
        return Optional.ofNullable(System.getenv(property.name()));
    }

    @SneakyThrows
    HttpRequestFactory getRequestFactory(HttpRequest request) {
        // per connection request factory, abstracts auth ..
        HttpTransport transport = new NetHttpTransport();

        //TODO: changing impl of credentials/initializer should support sources authenticated by
        // something OTHER than a Google Service account
        // eg, OAuth2CredentialsWithRefresh.newBuilder(), etc ..

        //assume that in cloud function env, this will get ours ...
        Optional<String> serviceAccountUser =
            getHeader(request, ControlHeader.SERVICE_ACCOUNT_USER)
                .map(values -> values.stream().findFirst().orElseThrow());

        serviceAccountUser.ifPresentOrElse(
            user -> log.info("Service account user: " + user),
            () -> log.warning("we usually expect a Service Account User"));

        GoogleCredentials credentials = quietGetApplicationDefault(serviceAccountUser,
            Arrays.stream(getRequiredConfigProperty(ConfigProperty.OAUTH_SCOPES).split(",")).collect(Collectors.toSet()));



        HttpCredentialsAdapter initializer = new HttpCredentialsAdapter(credentials);

        //TODO: in OAuth-type use cases, where execute() may have caused token to be refreshed, how
        // do we capture the new one?? ideally do this with listener/handler/trigger in Credential
        // itself, if that's possible


        return transport.createRequestFactory(initializer);
    }

    /**
     * - not needed atm, but may use in future to support non GCP-envs - although if can configure
     * ENV_VAR, probably all equivalent1?!?
     *
     * quiet helper to avoid try-catch
     *
     * @param encoded
     * @param serviceAccountUser
     * @param scopes
     * @return
     */
    @SneakyThrows
    GoogleCredentials quietFromBase64String(String encoded, Optional<String> serviceAccountUser, Set<String> scopes) {
        ServiceAccountCredentials credentials =
            ServiceAccountCredentials.fromStream(new ByteArrayInputStream(
                Base64.getDecoder().decode(encoded.getBytes(StandardCharsets.UTF_8))));

        //q: does below work in environments where application default is NOT a service account??

        ServiceAccountCredentials.Builder builder = credentials.toBuilder()
            .setScopes(scopes);
        if (serviceAccountUser.isPresent()) {
            builder = builder.setServiceAccountUser(serviceAccountUser.get());
        }
        return builder.build();
    }



    /**
     * quiet helper to avoid try-catch
     *
     * @return
     */
    @SneakyThrows
    GoogleCredentials quietGetApplicationDefault(Optional<String> serviceAccountUser, Set<String> scopes) {
        GoogleCredentials credentials = ServiceAccountCredentials.getApplicationDefault();

        if (serviceAccountUser.isPresent()) {
            credentials = credentials.createDelegated(serviceAccountUser.get());
        }
        //TODO: above are NOT working in the serviceAccountUser mode in the cloud!?!?

        credentials = credentials.createScoped(scopes);

        log.info("Credentials: " + credentials.toString());

        return credentials;
    }
}
