package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.utils.URLUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.common.net.MediaType;
import lombok.*;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class CommonRequestHandler {

    //we have ~540 total in Cloud Function connection, so can have generous values here
    final int SOURCE_API_REQUEST_CONNECT_TIMEOUT_MILLISECONDS = 30_000;
    final int SOURCE_API_REQUEST_READ_TIMEOUT = 300_000;

    @Inject ConfigService config;
    @Inject RulesUtils rulesUtils;
    @Inject SourceAuthStrategy sourceAuthStrategy;
    @Inject ObjectMapper objectMapper;
    @Inject SanitizerFactory sanitizerFactory;
    @Inject Rules rules;

    private Sanitizer sanitizer;

    @SneakyThrows
    public HttpEventResponse handle(HttpEventRequest request) {

        //TODO: cache this?? no point in re-parsing config each time ...
        this.sanitizer = sanitizerFactory.create(sanitizerFactory.buildOptions(config, rules));

        boolean isHealthCheck =
            request.getHeader(ControlHeader.HEALTH_CHECK.getHttpHeader()).isPresent();
        if (isHealthCheck) {
            return doHealthCheck(request);
        }

        HttpRequestFactory requestFactory = getRequestFactory(request);

        // re-write host
        //TODO: switch on method to support HEAD, etc
        URL targetUrl = buildTarget(request);

        boolean skipSanitizer = config.isDevelopment() &&
            isRequestedToSkipSanitizer(request);

        HttpEventResponse.HttpEventResponseBuilder builder = HttpEventResponse.builder();

        if (skipSanitizer || sanitizer.isAllowed(targetUrl)) {
            log.info("Proxy invoked with target " + URLUtils.relativeURL(targetUrl));
        } else {
            builder.statusCode(403);
            log.warning("Attempt to call endpoint blocked by rules: " + targetUrl + "; rules " + objectMapper.writeValueAsString(rules.getAllowedEndpointRegexes()));
            return builder.build();
        }

        com.google.api.client.http.HttpRequest sourceApiRequest;
        try {
            sourceApiRequest =
                requestFactory.buildGetRequest(new GenericUrl(targetUrl.toString()));
        } catch (IOException e) {
            builder.statusCode(500);
            builder.body("Failed to authorize request; review logs");
            log.log(Level.WARNING, e.getMessage(), e);

            //something like "Error getting access token for service account: 401 Unauthorized POST https://oauth2.googleapis.com/token,"
            log.log(Level.WARNING, "Confirm oauth scopes set in config.yaml match those granted via Google Workspace Admin Console");
            return builder.build();
        }

        //TODO: what headers to forward???

        //setup request
        sourceApiRequest
            .setThrowExceptionOnExecuteError(false)
            .setConnectTimeout(SOURCE_API_REQUEST_CONNECT_TIMEOUT_MILLISECONDS)
            .setReadTimeout(SOURCE_API_REQUEST_READ_TIMEOUT);

        //q: add exception handlers for IOExceptions / HTTP error responses, so those retries
        // happen in proxy rather than on Worklytics-side?

        com.google.api.client.http.HttpResponse sourceApiResponse = sourceApiRequest.execute();

        // return response
        builder.statusCode(sourceApiResponse.getStatusCode());

        String responseContent =
            new String(sourceApiResponse.getContent().readAllBytes(), sourceApiResponse.getContentCharset());

        //log.info(sourceApiResponse.toString());
        //builder.header(Pair.of("Content-Type", sourceApiResponse.getContentType()));

        String proxyResponseContent;
        if (isSuccessFamily(sourceApiResponse.getStatusCode())) {
            if (skipSanitizer) {
                proxyResponseContent = responseContent;
            }  else {
                proxyResponseContent = sanitizer.sanitize(targetUrl, responseContent);
                String rulesSha = rulesUtils.sha(sanitizer.getOptions().getRules());
                builder.header(ResponseHeader.RULES_SHA.getHttpHeader(), rulesSha);
                log.info("response sanitized with rule set " + rulesSha);
            }
        } else {
            //write error, which shouldn't contain PII, directly
            log.log(Level.WARNING, "Source API Error " + responseContent);
            //TODO: could run this through DLP to be extra safe
            proxyResponseContent = responseContent;
        }

        builder.body(proxyResponseContent);

        return builder.build();
    }

    @SneakyThrows
    HttpRequestFactory getRequestFactory(HttpEventRequest request) {
        // per connection request factory, abstracts auth ..
        HttpTransport transport = new NetHttpTransport();

        //TODO: changing impl of credentials/initializer should support sources authenticated by
        // something OTHER than a Google Service account
        // eg, OAuth2CredentialsWithRefresh.newBuilder(), etc ..

        //assume that in cloud function env, this will get ours ...
        Optional<String> accountToImpersonate =
           request.getHeader(ControlHeader.USER_TO_IMPERSONATE.getHttpHeader());

        accountToImpersonate.ifPresent(user -> log.info("Impersonating user"));
        //TODO: warn here for Google Workspace connectors, which expect user??

        Credentials credentials = sourceAuthStrategy.getCredentials(accountToImpersonate);
        HttpCredentialsAdapter initializer = new HttpCredentialsAdapter(credentials);

        //TODO: in OAuth-type use cases, where execute() may have caused token to be refreshed, how
        // do we capture the new one?? ideally do this with listener/handler/trigger in Credential
        // itself, if that's possible

        return transport.createRequestFactory(initializer);
    }

    boolean isRequestedToSkipSanitizer(HttpEventRequest request) {
        return request.getHeader( ControlHeader.SKIP_SANITIZER.getHttpHeader())
            .map(Boolean::parseBoolean)
            .orElse(false);
    }

    private HttpEventResponse doHealthCheck(HttpEventRequest request) {
        Set<String> missing =
            sourceAuthStrategy.getRequiredConfigProperties().stream()
                .filter(configProperty -> config.getConfigPropertyAsOptional(configProperty).isEmpty())
                .map(ConfigService.ConfigProperty::name)
                .collect(Collectors.toSet());

        HealthCheckResult healthCheckResult = HealthCheckResult.builder()
            .configuredSource(config.getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE).orElse(null))
            .nonDefaultSalt(config.getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT).isPresent())
            .missingConfigProperties(missing)
            .build();

        HttpEventResponse.HttpEventResponseBuilder responseBuilder = HttpEventResponse.builder();

        if (healthCheckResult.passed()) {
            responseBuilder.statusCode(HealthCheckResult.HttpStatusCode.SUCCEED.getCode());
        } else {
            responseBuilder.statusCode(HealthCheckResult.HttpStatusCode.FAIL.getCode());
        }

        try {
            responseBuilder.header("Content-Type", MediaType.JSON_UTF_8.toString());
            responseBuilder.body(
                objectMapper.writeValueAsString(healthCheckResult) + "\r\n");
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to write health check details", e);
        }

        return responseBuilder.build();
    }

    boolean isSuccessFamily (int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @SneakyThrows
    public URL buildTarget(HttpEventRequest request) {
        String path =
            request.getPath()
                + request.getQuery().map(s -> "?" + s).orElse("");

        return new URL("https", config.getConfigPropertyOrError(ProxyConfigProperty.TARGET_HOST), path);
    }

}
