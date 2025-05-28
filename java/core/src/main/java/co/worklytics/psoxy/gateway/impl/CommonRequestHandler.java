package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.*;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.utils.ComposedHttpRequestInitializer;
import co.worklytics.psoxy.utils.GzipedContentHttpRequestInitializer;
import co.worklytics.psoxy.utils.URLUtils;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.*;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.http.HttpTransportFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URL;
import java.util.*;
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

    @Inject
    EnvVarsConfigService envVarsConfigService;
    @Inject
    ConfigService config;
    @Inject
    SecretStore secretStore;
    @Inject
    RulesUtils rulesUtils;
    @Inject
    SourceAuthStrategy sourceAuthStrategy;
    @Inject
    ObjectMapper objectMapper;
    @Inject
    RESTApiSanitizerFactory sanitizerFactory;
    @Inject
    PseudonymizerImplFactory pseudonymizerImplFactory;
    @Inject
    RESTRules rules;
    @Inject
    HealthCheckRequestHandler healthCheckRequestHandler;
    @Inject
    ReversibleTokenizationStrategy reversibleTokenizationStrategy;
    @Inject
    UrlSafeTokenPseudonymEncoder pseudonymEncoder;
    @Inject
    HttpTransportFactory httpTransportFactory;

    @Inject @Named("forOriginal")
    SideOutput sideOutput;
    @Inject @Named("forSanitized")
    SideOutput sideOutputSanitized;


    /**
     * Basic headers to pass: content, caching, retries. Can be expanded by connection later.
     * Matches literally on headers.
     *
     * @see #passThroughHeaders(HttpEventResponse.HttpEventResponseBuilder, HttpResponse)
     * @see <a href="https://flaviocopes.com/http-response-headers/"></a>
     * @see <a href="https://developer.mozilla.org/en-US/docs/Glossary/Response_header"></a>
     */
    public static Set<String> DEFAULT_HEADERS_PASS_THROUGH = normalizeHeaders(Set.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CACHE_CONTROL,
            HttpHeaders.ETAG,
            HttpHeaders.LINK,
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
     * name into one "field-name: field-value" pair, without changing the
     * semantics of the message, by appending each subsequent field value to
     * the combined field value in order, separated by a comma."
     */
    private static final Joiner HEADER_JOINER = Joiner.on(",");

    @VisibleForTesting
    volatile RESTApiSanitizer sanitizer;

    private final Object $writeLock = new Object[0];

    private RESTApiSanitizer loadSanitizerRules() {
        if (this.sanitizer == null) {
            synchronized ($writeLock) {
                if (this.sanitizer == null) {
                    Pseudonymizer.ConfigurationOptions options =
                            pseudonymizerImplFactory.buildOptions(config);
                    this.sanitizer = sanitizerFactory.create(rules, pseudonymizerImplFactory.create(options));
                }
            }
        }
        return this.sanitizer;
    }

    @SneakyThrows
    public HttpEventResponse handle(HttpEventRequest request) {

        logRequestIfVerbose(request);

        // application-level enforcement of HTTPS
        // (NOTE: should be redundant with infrastructure-level configuration)
        if (!request.isHttps().orElse(true)) {
            return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_BAD_REQUEST)
                    .header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.HTTPS_REQUIRED.name())
                    .body("Requests MUST be sent over HTTPS")
                    .build();
        }

        Optional<HttpEventResponse> healthCheckResponse = healthCheckRequestHandler.handleIfHealthCheck(request);
        if (healthCheckResponse.isPresent()) {
            return healthCheckResponse.get();
        }

        RequestUrls requestUrls;
        try {
            requestUrls = buildRequestedUrls(request);
        } catch (Throwable e) {
            //really shouldn't happen ... parsing one url from another, so would be a bad bug in our canonicalization code for this to go wrong
            log.log(Level.WARNING, "Error parsing  / building request URL", e);
            return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.FAILED_TO_BUILD_URL.name())
                    .body("Error parsing request URL")
                    .build();
        }

        // avoid logging clear URL outside of dev
        URL toLog = envVarsConfigService.isDevelopment() ? requestUrls.getTarget() : requestUrls.getOriginal();

        boolean skipSanitization = clientRequestsSkipSanization(request);
        boolean clientRequestsNoResponse = clientRequestsNoResponse(request);

        HttpEventResponse.HttpEventResponseBuilder builder = HttpEventResponse.builder();

        try {
            this.sanitizer = loadSanitizerRules();
        } catch (Throwable e) {
            log.log(Level.SEVERE, "Error loading sanitizer rules", e);
            return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.CONFIGURATION_FAILURE.name())
                    .body("Error loading sanitizer rules")
                    .build();
        }

        //build log entry
        String logEntry = String.format("%s %s TokenInUrlDecrypted=%b", request.getHttpMethod(), URLUtils.relativeURL(toLog), requestUrls.hasDecryptedTokens());
        if (request.getClientIp().isPresent()) {
            // client IP is NOT available for direct AWS Lambda calls; only for API Gateway.
            // don't want to put blank/unknown in direct case, so log IP conditionally
            logEntry += String.format(" ClientIP=%s", request.getClientIp().get());
        }

        if (skipSanitization) {
            log.info(String.format("%s. Skipping sanitization.", logEntry));
        } else if (sanitizer.isAllowed(request.getHttpMethod(), requestUrls.getOriginal())) {
            log.info(String.format("%s. Rules allowed call.", logEntry));
        } else {
            builder.statusCode(HttpStatus.SC_FORBIDDEN);
            builder.header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.BLOCKED_BY_RULES.name());
            log.warning(String.format("%s. Blocked call by rules %s", logEntry, objectMapper.writeValueAsString(rules)));
            return builder.build();
        }

        com.google.api.client.http.HttpRequest sourceApiRequest;
        try {
            HttpRequestFactory requestFactory = getRequestFactory(request);

            HttpContent content = null;

            if (request.getBody() != null) {
                String contentType = request.getHeader(HttpHeaders.CONTENT_TYPE)
                        .orElse("application/json");
                content = new ByteArrayContent(contentType, request.getBody());
            }

            sourceApiRequest = requestFactory.buildRequest(request.getHttpMethod(), new GenericUrl(requestUrls.getTarget()), content);

            //TODO: what headers to forward???
            populateHeadersFromSource(sourceApiRequest, request, requestUrls.getTarget());

            //setup request
            sourceApiRequest
                .setThrowExceptionOnExecuteError(false)
                .setConnectTimeout(SOURCE_API_REQUEST_CONNECT_TIMEOUT_MILLISECONDS)
                .setReadTimeout(SOURCE_API_REQUEST_READ_TIMEOUT);

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

        final com.google.api.client.http.HttpResponse sourceApiResponse;
        try {
            //q: add exception handlers for IOExceptions / HTTP error responses, so those retries
            // happen in proxy rather than on Worklytics-side?
            sourceApiResponse = sourceApiRequest.execute();
        } catch (ConnectException e) {
            //connectivity problems
            builder.statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
            builder.header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.CONNECTION_TO_SOURCE.name());
            builder.body("Error connecting to source API: " + e.getMessage());
            log.log(Level.SEVERE, "Error connecting to source API: " + e.getMessage(), e);
            return builder.build();
        }


        String proxyResponseContent = "";
        try {
            // return response
            builder.statusCode(sourceApiResponse.getStatusCode());

            // TODO: if side output cases of the original, we *could* use the potentially compressed stream directly, instead of reading to a string?
            ProcessedContent original = this.asProcessedContent(sourceApiResponse);
            sideOutput.write(request, original);

            passThroughHeaders(builder, sourceApiResponse);
            if (isSuccessFamily(sourceApiResponse.getStatusCode())) {
                if (clientRequestsNoResponse) {
                    // client doesn't want a response body; assume there's a side output (otherwise we don't need to do this at all)
                    new Thread(() -> {
                       ProcessedContent sanitizedContent = sanitize(request, requestUrls, original);
                        writeSideOutput(request, sourceApiResponse, sanitizedContent);
                    }).start();

                    return builder.build();
                } else {
                    // client wants a response body
                    ProcessedContent sanitizationResult  =
                        sanitize(request, requestUrls, original);
                    proxyResponseContent = sanitizationResult.getContent();

                    sanitizationResult.getMetadata().entrySet()
                        .forEach(e -> builder.header(e.getKey(), e.getValue()));

                    writeSideOutput(request, sourceApiResponse, sanitizationResult);
                }

                if (skipSanitization) {
                    proxyResponseContent = original.getContent();
                }
            } else {
                //write error, which shouldn't contain PII, directly
                log.log(Level.WARNING, "Source API Error " + original.getContent());
                builder.header(ResponseHeader.ERROR.getHttpHeader(), ErrorCauses.API_ERROR.name());
                proxyResponseContent = original.getContent();
            }
            builder.body(proxyResponseContent);
            return builder.build();
        } finally {
            sourceApiResponse.disconnect();
        }
    }

    ProcessedContent asProcessedContent(HttpResponse sourceApiResponse) throws IOException {
        ProcessedContent.ProcessedContentBuilder builder = ProcessedContent.builder()
            .contentType(sourceApiResponse.getContentType())
            .contentCharset(sourceApiResponse.getContentCharset());

        try (InputStream stream = sourceApiResponse.getContent()) {
            builder.content(new String(stream.readAllBytes(), sourceApiResponse.getContentCharset()));
        }

        return builder.build();
    }

    ProcessedContent sanitize(HttpEventRequest request, RequestUrls requestUrls, ProcessedContent originalContent) {
        RESTApiSanitizer sanitizerForRequest = getSanitizerForRequest(request);
        String sanitized = StringUtils.trimToEmpty(sanitizerForRequest.sanitize(request.getHttpMethod(), requestUrls.getOriginal(), originalContent.getContent()));

        String rulesSha = rulesUtils.sha(sanitizerForRequest.getRules());
        log.info("response sanitized with rule set " + rulesSha);

        Map<String, String> metadata = new HashMap<>(originalContent.getMetadata());
        metadata.put(ResponseHeader.RULES_SHA.getHttpHeader(), rulesSha);

        return ProcessedContent.builder()
            .contentType(originalContent.getContentType())
            .contentCharset(originalContent.getContentCharset())
            .metadata(metadata)
            .content(sanitized)
            .build();
    }


    void writeSideOutput(HttpEventRequest request, com.google.api.client.http.HttpResponse sourceApiResponse, ProcessedContent sanitizedContent) {
        try {
            sideOutputSanitized.write(request, sanitizedContent);
        } catch (IOException e) {
            log.log(Level.WARNING, "Error writing to side output", e);
        }
    }


    @Value
    static class RequestUrls {

        /**
         * original URL, as requested
         */
        URL original;

        /**
         * decrypted URL, if tokenized components were found
         */
        URL target;

        boolean hasDecryptedTokens() {
            return !original.equals(target);
        }
    }

    /**
     * encapsulates dynamically configuring Sanitizer based on request (to support some aspects of
     * its behavior being controlled via HTTP headers)
     *
     * @param request to get sanitizer for
     */
    RESTApiSanitizer getSanitizerForRequest(HttpEventRequest request) {
        Optional<PseudonymImplementation> pseudonymImplementation = parsePseudonymImplementation(request);
        if (pseudonymImplementation.isPresent()) {
            loadSanitizerRules(); // ensure sanitizer is loaded
            if (!Objects.equals(pseudonymImplementation.get(),
                    sanitizer.getPseudonymizer().getOptions().getPseudonymImplementation())) {
                return sanitizerFactory.create(rules,
                        pseudonymizerImplFactory.create(sanitizer.getPseudonymizer().getOptions().withPseudonymImplementation(pseudonymImplementation.get())));
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

    @VisibleForTesting
    @SneakyThrows // MalformedURLException, but that really shouldn't happen!!
    RequestUrls buildRequestedUrls(HttpEventRequest request) {

        // parse requested URL, re-writing host/etc
        String requestedTargetUrl = parseRequestedTarget(request);

        // if requested target URL has tokenized components, reverse
        String clearTargetUrl = reverseTokenizedUrlComponents(requestedTargetUrl);

        // Using original URL to check sanitized rules, as they should match the original URL. It could contain tokenized components.
        // Examples:
        // /v1/accounts/p~12adsfasdfasdf31
        // /v1/accounts/12345
        URL originalRequestedURL = new URL(requestedTargetUrl);
        // And the URL to use for source request; it could contain the reversed tokenized components
        URL targetForSourceApiRequest = new URL(clearTargetUrl);

        return new RequestUrls(originalRequestedURL, targetForSourceApiRequest);
    }

    /**
     * side effects: modifies the responseBuilder, adding the headers to pass through
     *
     * @param responseBuilder - the proxy response being built
     * @param response        - the original response from the upstream API
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

        //q: right idea here? or google-http-client provides factory implementation, but whole point of DI fw is to avoid factories
        //
        HttpTransport transport = httpTransportFactory.create();

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
     *
     * @param request
     * @return
     */
    private boolean clientRequestsSkipSanization(HttpEventRequest request) {
        if (envVarsConfigService.isDevelopment()) {
            // caller requested to skip
            return request.getHeader(ControlHeader.SKIP_SANITIZER.getHttpHeader())
                .map(Boolean::parseBoolean)
                .orElse(false);
        } else {
            return false;
        }
    }

     /**
      * whether client actually wants a response body
      *
      * use-case: API that returns very large response bodies that take too long to process synchronously within a single HTTP request (eg, < 55s)
      *   clients can use this to indicate they don't want a response body, and will pick it up asynchronously from a side output later if they care to
      *
      *
       */
    private boolean clientRequestsNoResponse(HttpEventRequest request) {
         return request.getHeader(ControlHeader.NO_RESPONSE_BODY.getHttpHeader())
                    .map(Boolean::parseBoolean)
                    .orElse(false);
    }

    private void logRequestIfVerbose(HttpEventRequest request) {
        if (envVarsConfigService.isDevelopment()) {
            log.info(request.prettyPrint());
        }
    }

    boolean isSuccessFamily(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @SneakyThrows
    String parseRequestedTarget(HttpEventRequest request) {
        // contents may come encoded. It should respect url as it comes.
        // Construct URL directly concatenating instead of URIBuilder as it may re-encode.
        URIBuilder uriBuilder = new URIBuilder();
        uriBuilder.setScheme("https");
        uriBuilder.setHost(config.getConfigPropertyOrError(ProxyConfigProperty.TARGET_HOST));
        URL hostURL = uriBuilder.build().toURL();
        String hostPlusPath =
                StringUtils.stripEnd(hostURL.toString(), "/") + "/" +
                        StringUtils.stripStart(request.getPath(), "/");
        String targetURLString = hostPlusPath;
        if (StringUtils.isNotBlank(request.getQuery().orElse(null))) {
            targetURLString = hostPlusPath + "?" + request.getQuery().get();
        }
        return targetURLString;
    }


    // NOTE: not 'decrypt', as that is only one possible implementation of reversible tokenization
    String reverseTokenizedUrlComponents(String encodedURL) {
        return pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(encodedURL, reversibleTokenizationStrategy);
    }

    private void logIfDevelopmentMode(Supplier<String> messageSupplier) {
        if (envVarsConfigService.isDevelopment()) {
            log.info(messageSupplier.get());
        }
    }

    private void populateHeadersFromSource(HttpRequest sourceApiRequest, HttpEventRequest request, URL targetUrl) {
        com.google.api.client.http.HttpHeaders headers = sourceApiRequest.getHeaders();

        //seems like Google API HTTP client has a default 'Accept' header with 'text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2' ??
        //MSFT gives weird "{"error":{"code":"InternalServerError","message":"The MIME type 'text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2' requires a '/' character between type and subtype, such as 'text/plain'."}}
        headers.setAccept(ContentType.APPLICATION_JSON.toString());

        sanitizer.getAllowedHeadersToForward(request.getHttpMethod(), targetUrl)
                .ifPresent(i -> i.forEach(h ->
                    request.getHeader(h).ifPresent(headerValue -> {
                        logIfDevelopmentMode(() -> String.format("Header %s included", h));
                        headers.set(h, headerValue);
                })));
    }
}
