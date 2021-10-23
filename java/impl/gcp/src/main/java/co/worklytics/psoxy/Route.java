package co.worklytics.psoxy;

import co.worklytics.psoxy.impl.EnvVarsConfigService;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * config properties that control basic proxy behavior
     */
    enum ProxyConfigProperty implements ConfigService.ConfigProperty {
        PSOXY_SALT,
        SOURCE,
        IDENTIFIER_SCOPE_ID,
        //target API endpoint to forward request to
        TARGET_HOST,
    }

    /**
     * config properties that control how Psoxy authenticates against host
     */
    enum SourceAuthConfigProperty implements ConfigService.ConfigProperty {
        OAUTH_SCOPES,
        //this should ACTUALLY be stored in secret manager, and then exposed as env var to the
        // cloud function
        // see "https://cloud.google.com/functions/docs/configuring/secrets#gcloud"
        SERVICE_ACCOUNT_KEY,
    }


    /**
     * see "https://cloud.google.com/functions/docs/configuring/env-var"
     */
    enum RuntimeEnvironmentVariables {
        K_SERVICE,
    }

    /**
     * default value for salt; provided just to support testing with minimal config, but in prod
     * use should be overridden with something
     */
    static final String DEFAULT_SALT = "salt";


    ConfigService config;
    Sanitizer sanitizer;

    ConfigService getConfig() {
        if (config == null) {
            /**
             * in GCP cloud function, we should be able to configure everything via env vars; either
             * directly or by binding them to secrets at function deployment:
             *
             * @see "https://cloud.google.com/functions/docs/configuring/env-var"
             * @see "https://cloud.google.com/functions/docs/configuring/secrets"
             */
            config = new EnvVarsConfigService();
        }
        return config;
    }

    void initSanitizer() {
        Rules rules = PrebuiltSanitizerRules.MAP.get(getConfig().getConfigPropertyOrError(ProxyConfigProperty.SOURCE));
        sanitizer = new SanitizerImpl(
            Sanitizer.Options.builder()
                .rules(rules)
                .pseudonymizationSalt(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                    .orElse(DEFAULT_SALT))
                .defaultScopeId(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.IDENTIFIER_SCOPE_ID)
                    .orElse(rules.getDefaultScopeIdForSource()))
                .build());
    }

    @Override
    public void service(HttpRequest request, HttpResponse response)
            throws IOException {

        boolean isHealthCheck =
            request.getHeaders().containsKey(ControlHeader.HEALTH_CHECK.getHttpHeader());
        if (isHealthCheck) {
            doHealthCheck(request, response);
            return;
        }

        if (sanitizer == null) {
            initSanitizer();
        }


        //is there a lifecycle to initialize request factory??
        HttpRequestFactory requestFactory = getRequestFactory(request);
        //q: what about when request factory needs to set a service account user?

        // re-write host
        //TODO: switch on method to support HEAD, etc
        URL targetUrl = buildTarget(request);

        log.info("Proxy invoked with target: " + targetUrl.getPath() + "?" + targetUrl.getQuery());

        //TODO: test URL against blacklist regex??

        com.google.api.client.http.HttpRequest sourceApiRequest =
            requestFactory.buildGetRequest(new GenericUrl(targetUrl.toString()));

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

    private void doHealthCheck(HttpRequest request, HttpResponse response) {
        ObjectMapper objectMapper = new ObjectMapper();

        HealthCheckResult healthCheckResult = HealthCheckResult.builder()
            .configuredSource(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE).orElse(null))
            .nonDefaultSalt(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT).isPresent())
            .build();

        if (healthCheckResult.passed()) {
            response.setStatusCode(200, "Health check passed");
        } else {
            response.setStatusCode(HealthCheckResult.HTTP_SC_FAIL, "Health check failed");
        }

        try {
            //TODO: get this from a constant? would add dep on some HTTP lib that has it, such as
            // JAX-RS,
            // Guava (https://guava.dev/releases/27.0-jre/api/docs/com/google/common/net/MediaType.html)
            // Spring (https://docs.spring.io/spring-framework/docs/3.0.x/javadoc-api/org/springframework/http/MediaType.html)
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(healthCheckResult));
            response.getWriter().write("\r\n");
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to write health check details", e);
        }
    }

    boolean isSuccessFamily (int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }


    @SneakyThrows
    URL buildTarget(HttpRequest request) {
        String path =
            request.getPath()
                .replace(System.getenv(RuntimeEnvironmentVariables.K_SERVICE.name()) + "/", "")
            + request.getQuery().map(s -> "?" + s).orElse("");

        return new URL("https", getConfig().getConfigPropertyOrError(ProxyConfigProperty.TARGET_HOST), path);
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
            Arrays.stream(getConfig().getConfigPropertyOrError(SourceAuthConfigProperty.OAUTH_SCOPES).split(","))
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
            String key = getConfig().getConfigPropertyOrError(SourceAuthConfigProperty.SERVICE_ACCOUNT_KEY);
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
