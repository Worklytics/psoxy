package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.rules.RuleSet;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.utils.ComposedHttpRequestInitializer;
import co.worklytics.psoxy.utils.GzipedContentHttpRequestInitializer;
import co.worklytics.psoxy.utils.URLUtils;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;

import javax.inject.Inject;
import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class CommonRequestHandler {

    //we have ~540 total in Cloud Function connection, so can have generous values here
    private static final int SOURCE_API_REQUEST_CONNECT_TIMEOUT_MILLISECONDS = 30_000;
    private static final int SOURCE_API_REQUEST_READ_TIMEOUT = 300_000;

    @Inject ConfigService config;
    @Inject RulesUtils rulesUtils;
    @Inject SourceAuthStrategy sourceAuthStrategy;
    @Inject ObjectMapper objectMapper;
    @Inject SanitizerFactory sanitizerFactory;
    @Inject
    RuleSet rules;
    @Inject HealthCheckRequestHandler healthCheckRequestHandler;
    @Inject
    ReversibleTokenizationStrategy reversibleTokenizationStrategy;
    @Inject UrlSafeTokenPseudonymEncoder pseudonymEncoder;

    /**
     * Basic headers to pass: content, caching, retries. Can be expanded by connection later.
     * Matches literally on headers.
     * @see #passThroughHeaders(HttpEventResponse.HttpEventResponseBuilder, HttpResponse)
     * @see <a href="https://flaviocopes.com/http-response-headers/"></a>
     * @see <a href="https://developer.mozilla.org/en-US/docs/Glossary/Response_header"></a>
     */
    public static Set<String> DEFAULT_HEADERS_PASS_THROUGH = normalizeHeaders(Set.of(
        HttpHeaders.CONTENT_TYPE,
        HttpHeaders.CACHE_CONTROL,
        HttpHeaders.ETAG,
        HttpHeaders.EXPIRES,
        HttpHeaders.LAST_MODIFIED,
        HttpHeaders.RETRY_AFTER
    ));

    /**
     * Patters to look for in headers to pass through
     *
     * @see #passThroughHeaders(HttpEventResponse.HttpEventResponseBuilder, HttpResponse)
     */
    public Set<Pattern> RE_MATCH_HEADERS_PASS_THROUGH = ImmutableSet.of(
        Pattern.compile(normalizeHeader("X-RateLimit.*"))
    );

    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc7230#section-3.2.2">rfc7230</a>
     * "... A recipient MAY combine multiple header fields with the same field
     *    name into one "field-name: field-value" pair, without changing the
     *    semantics of the message, by appending each subsequent field value to
     *    the combined field value in order, separated by a comma."
     */
    private static final Joiner HEADER_JOINER = Joiner.on(",");


    private volatile Sanitizer sanitizer;
    private final Object $writeLock = new Object[0];

    private Sanitizer loadSanitizerRules() {
        if (this.sanitizer == null) {
            synchronized ($writeLock) {
                if (this.sanitizer == null) {
                    this.sanitizer = sanitizerFactory.create(sanitizerFactory.buildOptions(config, rules));
                }
            }
        }
        return this.sanitizer;
    }

    @SneakyThrows
    public HttpEventResponse handle(HttpEventRequest request) {

        logRequestIfAllowed(request);

        Optional<HttpEventResponse> healthCheckResponse = healthCheckRequestHandler.handleIfHealthCheck(request);
        if (healthCheckResponse.isPresent()) {
            return healthCheckResponse.get();
        }

        // re-write host
        URL targetUrl = buildTarget(request);
        String relativeURL = URLUtils.relativeURL(targetUrl);

        boolean skipSanitization = skipSanitization(request);

        HttpEventResponse.HttpEventResponseBuilder builder = HttpEventResponse.builder();

        this.sanitizer = loadSanitizerRules();

        String callLog = String.format("%s %s", request.getHttpMethod(), relativeURL);
        if (skipSanitization) {
            log.info(String.format("%s. Skipping sanitization.", callLog));
        } else if (sanitizer.isAllowed(request.getHttpMethod(), targetUrl)) {
            log.info(String.format("%s. Rules allowed call.", callLog));
        } else {
            builder.statusCode(HttpStatus.SC_FORBIDDEN);
            builder.header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.BLOCKED_BY_RULES.name());
            log.warning(String.format("%s. Blocked call by rules %s", callLog, objectMapper.writeValueAsString(rules)));
            return builder.build();
        }

        com.google.api.client.http.HttpRequest sourceApiRequest;
        try {
            HttpRequestFactory requestFactory = getRequestFactory(request);

            HttpContent content = null;

            if (request.getBody() != null) {
                String contentType = request.getHeader("content-type")
                        .orElse("application/json");
                content = new ByteArrayContent(contentType, request.getBody());
            }

            sourceApiRequest = requestFactory.buildRequest(request.getHttpMethod(), new GenericUrl(targetUrl), content);
        } catch (IOException e) {
            builder.statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            builder.body("Failed to parse request; review logs");
            builder.header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.CONNECTION_SETUP.name());
            log.log(Level.WARNING, e.getMessage(), e);
            //something like "Error getting access token for service account: 401 Unauthorized POST https://oauth2.googleapis.com/token,"
            log.log(Level.WARNING, "Confirm oauth scopes set in config.yaml match those granted in data source");
            return builder.build();
        } catch (java.util.NoSuchElementException e) {
            // missing config, such as ACCESS_TOKEN
            builder.statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            builder.header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.CONNECTION_SETUP.name());
            log.log(Level.WARNING, e.getMessage(), e);
            return builder.build();
        }

        //TODO: what headers to forward???
        sourceApiRequest.setHeaders(sourceApiRequest.getHeaders()
            //seems like Google API HTTP client has a default 'Accept' header with 'text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2' ??
            .setAccept(ContentType.APPLICATION_JSON.toString())  //MSFT gives weird "{"error":{"code":"InternalServerError","message":"The MIME type 'text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2' requires a '/' character between type and subtype, such as 'text/plain'."}}
        );

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

        try {
            // return response
            builder.statusCode(sourceApiResponse.getStatusCode());

            String responseContent = StringUtils.EMPTY;
            // could be empty in HEAD calls
            if (sourceApiResponse.getContent() != null) {
                responseContent = new String(sourceApiResponse.getContent().readAllBytes(), sourceApiResponse.getContentCharset());
            }

            passThroughHeaders(builder, sourceApiResponse);

            String proxyResponseContent;
            if (isSuccessFamily(sourceApiResponse.getStatusCode())) {
                if (skipSanitization) {
                    proxyResponseContent = responseContent;
                } else {
                    Sanitizer sanitizerForRequest = getSanitizerForRequest(request);

                    proxyResponseContent = sanitizerForRequest.sanitize(request.getHttpMethod(), targetUrl, responseContent);
                    String rulesSha = rulesUtils.sha(sanitizerForRequest.getConfigurationOptions().getRules());
                    builder.header(ResponseHeader.RULES_SHA.getHttpHeader(), rulesSha);
                    log.info("response sanitized with rule set " + rulesSha);
                }
            } else {
                //write error, which shouldn't contain PII, directly
                log.log(Level.WARNING, "Source API Error " + responseContent);
                //TODO: could run this through DLP to be extra safe
                builder.header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.API_ERROR.name());
                proxyResponseContent = responseContent;
            }
            builder.body(StringUtils.trimToEmpty(proxyResponseContent));
            return builder.build();
        } finally {
            sourceApiResponse.disconnect();
        }
    }



    /**
     * encapsulates dynamically configuring Sanitizer based on request (to support some aspects of
     * its behavior being controlled via HTTP headers)
     *
     * @param request
     */
    Sanitizer getSanitizerForRequest(HttpEventRequest request) {
        Optional<PseudonymImplementation> pseudonymImplementation = parsePseudonymImplementation(request);
        if (pseudonymImplementation.isPresent()) {
            loadSanitizerRules(); // ensure sanitizer is loaded
            if (!Objects.equals(pseudonymImplementation.get(),
                    sanitizer.getConfigurationOptions().getPseudonymImplementation())) {
                return sanitizerFactory.create(sanitizerFactory.buildOptions(config, rules)
                    .withPseudonymImplementation(pseudonymImplementation.get()));
            }
        }

        // just use the default
        return sanitizer;
    }

    @VisibleForTesting
    Optional<PseudonymImplementation> parsePseudonymImplementation(HttpEventRequest request) {
        return request.getHeader(ControlHeader.PSEUDONYM_IMPLEMENTATION.getHttpHeader())
            .map(PseudonymImplementation::parseHttpHeaderValue);
    }

    /**
     * side effects: modifies the responseBuilder, adding the headers to pass through
     * @param responseBuilder - the proxy response being built
     * @param response - the original response from the upstream API
     */
    @VisibleForTesting
    void passThroughHeaders(HttpEventResponse.HttpEventResponseBuilder responseBuilder, com.google.api.client.http.HttpResponse response) {
        Set<String> availableHeaders = normalizeHeaders(response.getHeaders().keySet());

        Sets.intersection(availableHeaders, DEFAULT_HEADERS_PASS_THROUGH).forEach(
            h -> responseBuilder.header(h, HEADER_JOINER.join(response.getHeaders().getHeaderStringValues(h)))
        );

        for (String availableHeader : availableHeaders) {
            if (RE_MATCH_HEADERS_PASS_THROUGH.stream().anyMatch(re -> re.matcher(availableHeader).matches())) {
                responseBuilder.header(availableHeader, HEADER_JOINER.join(response.getHeaders().getHeaderStringValues(availableHeader)));
            }
        }

        if (response.getContentType() != null) {
            responseBuilder.header(normalizeHeader(HttpHeaders.CONTENT_TYPE), response.getContentType());
        }
    }

    @VisibleForTesting
    static String normalizeHeader(String header) {
        return header.toLowerCase(Locale.US);
    }
    /**
     * @param headers header set
     * @return new set with headers normalized for comparisons
     */
    @VisibleForTesting
    static Set<String> normalizeHeaders(Set<String> headers) {
        return headers.stream().map(CommonRequestHandler::normalizeHeader).collect(Collectors.toUnmodifiableSet());
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

        accountToImpersonate = accountToImpersonate
            .map(s -> pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(s, reversibleTokenizationStrategy));

        Credentials credentials = sourceAuthStrategy.getCredentials(accountToImpersonate);
        HttpCredentialsAdapter initializeWithCredentials = new HttpCredentialsAdapter(credentials);

        //TODO: in OAuth-type use cases, where execute() may have caused token to be refreshed, how
        // do we capture the new one?? ideally do this with listener/handler/trigger in Credential
        // itself, if that's possible

        ComposedHttpRequestInitializer initializer =
            ComposedHttpRequestInitializer.of(initializeWithCredentials,
                new GzipedContentHttpRequestInitializer("Psoxy"));

        return transport.createRequestFactory(initializer);
    }

    /**
     * Only allowed under development mode
     * @param request
     * @return
     */
    private boolean skipSanitization(HttpEventRequest request) {
        if (config.isDevelopment()) {
            // caller requested to skip
            return request.getHeader(ControlHeader.SKIP_SANITIZER.getHttpHeader())
                .map(Boolean::parseBoolean)
                .orElse(false);
        } else {
            return false;
        }
    }

    private void logRequestIfAllowed(HttpEventRequest request) {
        logIfDevelopmentMode(() -> String.format("Request:\n%s", request.prettyPrint()));
    }

    boolean isSuccessFamily(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @SneakyThrows
    public URL buildTarget(HttpEventRequest request) {
        // contents may come encoded. It should respect url as it comes.
        // Construct URL directly concatenating instead of URIBuilder as it may re-encode.
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setScheme("https");
        uriBuilder.setHost(config.getConfigPropertyOrError(ProxyConfigProperty.TARGET_HOST));
        URL hostURL = uriBuilder.build().toURL();
        String hostPlusPath =
            StringUtils.stripEnd(hostURL.toString(),"/") + "/" +
            StringUtils.stripStart(request.getPath(),"/");
        String targetURLString = hostPlusPath;
        if (StringUtils.isNotBlank(request.getQuery().orElse(null))) {
            targetURLString = hostPlusPath + "?" + request.getQuery().get();
        }

        //TODO: configurable behavior?
        targetURLString = pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(targetURLString, reversibleTokenizationStrategy);

        return new URL(targetURLString);
    }

    private void logIfDevelopmentMode(Supplier<String> messageSupplier) {
        if (config.isDevelopment()) {
            log.info(messageSupplier.get());
        }
    }

}
