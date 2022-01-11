package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.EnvVarsConfigService;
import co.worklytics.psoxy.gateway.impl.OAuthAccessTokenSourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.OAuthRefreshTokenSourceAuthStrategy;
import co.worklytics.psoxy.impl.SanitizerImpl;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.rules.PrebuiltSanitizerRules;

import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.utils.URLUtils;
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
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import javax.inject.Inject;
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
    static final String PATH_TO_RULES_FILES = "/rules.yaml";

    @Getter
    @Inject ConfigService config;
    @Inject Set<SourceAuthStrategy> sourceAuthStrategies;
    @Inject SanitizerFactory sanitizerFactory;

    Sanitizer sanitizer;
    SourceAuthStrategy sourceAuthStrategy;
    RulesUtils rulesUtils = new RulesUtils();
    ObjectMapper objectMapper = new ObjectMapper();

    SourceAuthStrategy getSourceAuthStrategy() {
        if (sourceAuthStrategy == null) {
            String identifier = getConfig().getConfigPropertyOrError(ProxyConfigProperty.SOURCE_AUTH_STRATEGY_IDENTIFIER);
            sourceAuthStrategy = sourceAuthStrategies
                    .stream()
                    .filter(impl -> Objects.equals(identifier, impl.getConfigIdentifier()))
                    .findFirst()
                    .orElseThrow(() -> new Error("No SourceAuthStrategy impl matching configured identifier: " + identifier));
        }
        return sourceAuthStrategy;
    }

    void initSanitizer() {
        Optional<Rules> fileSystemRules = rulesUtils.getRulesFromFileSystem(PATH_TO_RULES_FILES);
        if (fileSystemRules.isPresent()) {
            log.info("using rules from file system");
        }
        Rules rules = fileSystemRules.orElseGet(() -> {
                Optional<Rules> configRules = rulesUtils.getRulesFromConfig(getConfig());
                if (configRules.isPresent()) {
                    log.info("using rules from environment config (RULES variable parsed as base64-encoded YAML)");
                }
                return configRules.orElseGet(() -> {
                    String source = getConfig().getConfigPropertyOrError(ProxyConfigProperty.SOURCE);
                    log.info("using prebuilt rules for: " + source);
                    return PrebuiltSanitizerRules.MAP.get(source);
                });
            });

        sanitizer = sanitizerFactory.create(
            Sanitizer.Options.builder()
                .rules(rules)
                .pseudonymizationSalt(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT)
                    .orElse(DEFAULT_SALT))
                .defaultScopeId(getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.IDENTIFIER_SCOPE_ID)
                    .orElse(rules.getDefaultScopeIdForSource()))
                .build());

        if (isDevelopmentMode()) {
            log.warning("Proxy instance configured in development mode (env var IS_DEVELOPMENT_MODE=true)");
        }
    }

    boolean isDevelopmentMode() {
        return config.getConfigPropertyAsOptional(ProxyConfigProperty.IS_DEVELOPMENT_MODE)
            .map(Boolean::parseBoolean).orElse(false);
    }

    boolean isRequestedToSkipSanitizer(HttpRequest request) {
        return request.getFirstHeader(ControlHeader.SKIP_SANITIZER.getHttpHeader())
            .map(Boolean::parseBoolean).orElse(false);
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

        // re-write host
        //TODO: switch on method to support HEAD, etc
        URL targetUrl = buildTarget(request);

        boolean skipSanitizer = isDevelopmentMode() && isRequestedToSkipSanitizer(request);

        if (skipSanitizer || sanitizer.isAllowed(targetUrl)) {
            log.info("Proxy invoked with target " + URLUtils.relativeURL(targetUrl));
        } else {
            response.setStatusCode(403, "Endpoint forbidden by proxy rule set");
            log.warning("Attempt to call endpoint blocked by rules: " + targetUrl);
            return;
        }

        com.google.api.client.http.HttpRequest sourceApiRequest;
        try {
            sourceApiRequest =
                requestFactory.buildGetRequest(new GenericUrl(targetUrl.toString()));
        } catch (IOException e) {
            response.setStatusCode(500);
            response.getWriter().write("Failed to authorize request; review logs");
            log.log(Level.WARNING, e.getMessage(), e);

            //something like "Error getting access token for service account: 401 Unauthorized POST https://oauth2.googleapis.com/token,"
            log.log(Level.WARNING, "Confirm oauth scopes set in config.yaml match those granted via Google Workspace Admin Console");
            return;
        }

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

        String proxyResponseContent;
        if (isSuccessFamily(sourceApiResponse.getStatusCode())) {
            if (skipSanitizer) {
                proxyResponseContent = responseContent;
            }  else {
                proxyResponseContent = sanitizer.sanitize(targetUrl, responseContent);
                String rulesSha = rulesUtils.sha(sanitizer.getOptions().getRules());
                response.appendHeader(ResponseHeader.RULES_SHA.getHttpHeader(), rulesSha);
                log.info("response sanitized with rule set " + rulesSha);
            }
        } else {
            //write error, which shouldn't contain PII, directly
            log.log(Level.WARNING, "Source API Error " + responseContent);
            //TODO: could run this through DLP to be extra safe
            proxyResponseContent = responseContent;
        }

        new ByteArrayInputStream(proxyResponseContent.getBytes(StandardCharsets.UTF_8))
            .transferTo(response.getOutputStream());
    }

    private void doHealthCheck(HttpRequest request, HttpResponse response) {
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
            user -> log.info("Impersonating user"),
            () -> log.warning("We usually expect a user to impersonate"));

        Credentials credentials = getSourceAuthStrategy().getCredentials(accountToImpersonate);
        HttpCredentialsAdapter initializer = new HttpCredentialsAdapter(credentials);

        //TODO: in OAuth-type use cases, where execute() may have caused token to be refreshed, how
        // do we capture the new one?? ideally do this with listener/handler/trigger in Credential
        // itself, if that's possible

        return transport.createRequestFactory(initializer);
    }

}
