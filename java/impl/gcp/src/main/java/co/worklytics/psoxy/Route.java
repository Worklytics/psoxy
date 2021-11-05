package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.OAuthAccessTokenSourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.google.PrebuiltSanitizerRules;
import co.worklytics.psoxy.gateway.ConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import com.google.common.net.MediaType;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.URL;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log
public class Route implements HttpFunction {

    //we have ~540 total in Cloud Function connection, so can have generous values here
    final int SOURCE_API_REQUEST_CONNECT_TIMEOUT = 30_000;
    final int SOURCE_API_REQUEST_READ_TIMEOUT = 300_000;

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
    SourceAuthStrategy sourceAuthStrategy;

    SourceAuthStrategy getSourceAuthStrategy() {
        if (sourceAuthStrategy == null) {
            String identifier = getConfig().getConfigPropertyOrError(ProxyConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER);
            Stream<SourceAuthStrategy> implementations = Stream.of(
                new GoogleCloudPlatformServiceAccountKeyAuthStrategy(),
                new OAuthRefreshTokenSourceAuthStrategy(),
                new OAuthAccessTokenSourceAuthStrategy()
            );
            sourceAuthStrategy = implementations
                    .filter(impl -> Objects.equals(identifier, impl.getConfigIdentifier()))
                .findFirst().orElseThrow(() -> new Error("No SourceAuthStrategy impl matching configured identifier: " + identifier));
        }
        return sourceAuthStrategy;
    }

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

        if (sanitizer.isAllowed(targetUrl)) {
            log.info("Proxy invoked with target: " + targetUrl.getPath() + "?" + targetUrl.getQuery());
        } else {
            response.setStatusCode(403, "Endpoint forbidden by proxy rule set");
            log.warning("Attempt to call endpoint blocked by rules: " + targetUrl);
            return;
        }

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

        Set<String> missing =
            getSourceAuthStrategy().getRequiredConfigProperties().stream()
                .filter(configProperty -> getConfig().getConfigPropertyAsOptional(configProperty).isEmpty())
                .map(ConfigService.ConfigProperty::name)
                .collect(Collectors.toSet());

        HealthCheckResult healthCheckResult = HealthCheckResult.builder()
            .configuredSource(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE).orElse(null))
            .nonDefaultSalt(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT).isPresent())
            .missingConfigProperties(missing)
            .build();

        if (healthCheckResult.passed()) {
            response.setStatusCode(HealthCheckResult.HttpStatusCode.SUCCEED.getCode(), "Health check passed");
        } else {
            response.setStatusCode(HealthCheckResult.HttpStatusCode.FAIL.getCode(), "Health check failed");
        }

        try {
            response.setContentType(MediaType.JSON_UTF_8.toString());
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

        Credentials credentials = getSourceAuthStrategy().getCredentials(accountToImpersonate);
        HttpCredentialsAdapter initializer = new HttpCredentialsAdapter(credentials);

        //TODO: in OAuth-type use cases, where execute() may have caused token to be refreshed, how
        // do we capture the new one?? ideally do this with listener/handler/trigger in Credential
        // itself, if that's possible


        return transport.createRequestFactory(initializer);
    }

}
