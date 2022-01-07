package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import co.worklytics.psoxy.gateway.ProxyRequestAdapter;
import co.worklytics.psoxy.utils.URLUtils;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.common.net.MediaType;
import lombok.*;
import lombok.extern.java.Log;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

@Log
@RequiredArgsConstructor
public class AbstractRequestHandler<R> {

    //we have ~540 total in Cloud Function connection, so can have generous values here
    final int SOURCE_API_REQUEST_CONNECT_TIMEOUT = 30_000;
    final int SOURCE_API_REQUEST_READ_TIMEOUT = 300_000;

    DependencyFactory dependencyFactory = new DependencyFactoryImpl();

    final ProxyRequestAdapter<R> requestAdapter;

    @Value
    @Builder
    public static class Response {

        int statusCode;

        @Singular
        List<Pair<String, String>> headers;

        String body;
    }

    @SneakyThrows
    public Response handle(R request) {

        boolean isHealthCheck =
            requestAdapter.getHeader(request, ControlHeader.HEALTH_CHECK.getHttpHeader()).isPresent();
        if (isHealthCheck) {
            return doHealthCheck(request);
        }

        HttpRequestFactory requestFactory = getRequestFactory(request);

        // re-write host
        //TODO: switch on method to support HEAD, etc
        URL targetUrl = buildTarget(request);

        boolean skipSanitizer = dependencyFactory.getConfig().isDevelopment() &&
            isRequestedToSkipSanitizer(request);

        Response.ResponseBuilder builder = Response.builder();

        if (skipSanitizer || dependencyFactory.getSanitizer().isAllowed(targetUrl)) {
            log.info("Proxy invoked with target " + URLUtils.relativeURL(targetUrl));
        } else {
            builder.statusCode(403);
            log.warning("Attempt to call endpoint blocked by rules: " + targetUrl);
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
            .setConnectTimeout(SOURCE_API_REQUEST_CONNECT_TIMEOUT)
            .setReadTimeout(SOURCE_API_REQUEST_READ_TIMEOUT);

        //q: add exception handlers for IOExceptions / HTTP error responses, so those retries
        // happen in proxy rather than on Worklytics-side?

        com.google.api.client.http.HttpResponse sourceApiResponse = sourceApiRequest.execute();

        // return response
        builder.statusCode(sourceApiResponse.getStatusCode());

        String responseContent =
            new String(sourceApiResponse.getContent().readAllBytes(), sourceApiResponse.getContentCharset());

        String proxyResponseContent;
        if (isSuccessFamily(sourceApiResponse.getStatusCode())) {
            if (skipSanitizer) {
                proxyResponseContent = responseContent;
            }  else {
                proxyResponseContent = dependencyFactory.getSanitizer().sanitize(targetUrl, responseContent);
                String rulesSha = dependencyFactory.getRulesUtils().sha(dependencyFactory.getSanitizer().getOptions().getRules());
                builder.header(Pair.of(ResponseHeader.RULES_SHA.getHttpHeader(), rulesSha));
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
    HttpRequestFactory getRequestFactory(R request) {
        // per connection request factory, abstracts auth ..
        HttpTransport transport = new NetHttpTransport();

        //TODO: changing impl of credentials/initializer should support sources authenticated by
        // something OTHER than a Google Service account
        // eg, OAuth2CredentialsWithRefresh.newBuilder(), etc ..

        //assume that in cloud function env, this will get ours ...
        Optional<String> accountToImpersonate =
           requestAdapter.getHeader(request, ControlHeader.USER_TO_IMPERSONATE.getHttpHeader())
                .map(values -> values.stream().findFirst().orElseThrow());

        accountToImpersonate.ifPresentOrElse(
            user -> log.info("Impersonating user"),
            () -> log.warning("We usually expect a user to impersonate"));

        Credentials credentials = dependencyFactory.getSourceAuthStrategy().getCredentials(accountToImpersonate);
        HttpCredentialsAdapter initializer = new HttpCredentialsAdapter(credentials);

        //TODO: in OAuth-type use cases, where execute() may have caused token to be refreshed, how
        // do we capture the new one?? ideally do this with listener/handler/trigger in Credential
        // itself, if that's possible

        return transport.createRequestFactory(initializer);
    }

    boolean isRequestedToSkipSanitizer(R request) {
        return requestAdapter.getHeader(request, ControlHeader.SKIP_SANITIZER.getHttpHeader())
            .map(values -> values.stream().findFirst().map(Boolean::parseBoolean).orElse(false))
            .orElse(false);
    }

    private Response doHealthCheck(R request) {
        Set<String> missing =
            dependencyFactory.getSourceAuthStrategy().getRequiredConfigProperties().stream()
                .filter(configProperty -> dependencyFactory.getConfig().getConfigPropertyAsOptional(configProperty).isEmpty())
                .map(ConfigService.ConfigProperty::name)
                .collect(Collectors.toSet());

        HealthCheckResult healthCheckResult = HealthCheckResult.builder()
            .configuredSource(dependencyFactory.getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.SOURCE).orElse(null))
            .nonDefaultSalt(dependencyFactory.getConfig().getConfigPropertyAsOptional(ProxyConfigProperty.PSOXY_SALT).isPresent())
            .missingConfigProperties(missing)
            .build();

        Response.ResponseBuilder responseBuilder = Response.builder();

        if (healthCheckResult.passed()) {
            responseBuilder.statusCode(HealthCheckResult.HttpStatusCode.SUCCEED.getCode());
        } else {
            responseBuilder.statusCode(HealthCheckResult.HttpStatusCode.FAIL.getCode());
        }

        try {
            responseBuilder.header(Pair.of("Content-Type", MediaType.JSON_UTF_8.toString()));
            responseBuilder.body(
                dependencyFactory.getObjectMapper().writeValueAsString(healthCheckResult) + "\r\n");
        } catch (IOException e) {
            log.log(Level.WARNING, "Failed to write health check details", e);
        }

        return responseBuilder.build();
    }

    boolean isSuccessFamily (int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @SneakyThrows
    public URL buildTarget(R request) {
        String path =
            requestAdapter.getPath(request)
                + requestAdapter.getQuery(request).map(s -> "?" + s).orElse("");

        return new URL("https", dependencyFactory.getConfig().getConfigPropertyOrError(ProxyConfigProperty.TARGET_HOST), path);
    }

}
