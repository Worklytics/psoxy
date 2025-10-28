package co.worklytics.psoxy.gateway.impl;

import java.io.*;
import java.net.ConnectException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import co.worklytics.psoxy.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.apache.hc.core5.net.WWWFormCodec;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import com.avaulta.gateway.pseudonyms.PseudonymImplementation;
import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.auth.Credentials;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.http.HttpTransportFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import co.worklytics.psoxy.gateway.ApiModeConfigProperty;
import co.worklytics.psoxy.gateway.AsyncApiDataRequestHandler;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.HttpEventResponse;
import co.worklytics.psoxy.gateway.ProcessedContent;
import co.worklytics.psoxy.gateway.SecretStore;
import co.worklytics.psoxy.gateway.SourceAuthStrategy;
import co.worklytics.psoxy.gateway.impl.output.OutputUtils;
import co.worklytics.psoxy.gateway.output.ApiDataOutputUtils;
import co.worklytics.psoxy.gateway.output.ApiDataSideOutput;
import co.worklytics.psoxy.gateway.output.ApiSanitizedDataOutput;
import co.worklytics.psoxy.gateway.output.Output;
import co.worklytics.psoxy.rules.RESTRules;
import co.worklytics.psoxy.rules.RulesUtils;
import co.worklytics.psoxy.utils.ComposedHttpRequestInitializer;
import co.worklytics.psoxy.utils.GzipedContentHttpRequestInitializer;
import co.worklytics.psoxy.utils.URLUtils;
import dagger.Lazy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.java.Log;

@NoArgsConstructor(onConstructor_ = @Inject)
@Log
public class ApiDataRequestHandler {

    // we have ~540 total in Cloud Function connection, so can have generous values here
    private static final int SOURCE_API_REQUEST_CONNECT_TIMEOUT_MS = 30_000; // 30 seconds
    private static final int SOURCE_API_REQUEST_READ_TIMEOUT_MS = 300_000; // 5 minutes

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
    @Inject
    @Named("async")
    Lazy<ApiSanitizedDataOutput> asyncSanitizedDataOutput;
    @Inject
    ApiDataSideOutput apiDataSideOutput;
    @Inject
    ApiSanitizedDataOutput apiDataSideOutputSanitized;
    @Inject
    ApiDataOutputUtils apiDataOutputUtils;
    @Inject
    ResponseCompressionHandler responseCompressionHandler;

    // lazy-loaded, to avoid circular dependency issues; and bc unused in 99.9% of situations
    @Inject
    Lazy<AsyncApiDataRequestHandler> asyncApiDataRequestHandler;

    /**
     * Basic headers to pass: content, caching, retries. Can be expanded by connection later.
     * Matches literally on headers.
     *
     * @see #passThroughHeaders(HttpEventResponse.HttpEventResponseBuilder, HttpResponse)
     * @see <a href="https://flaviocopes.com/http-response-headers/"></a>
     * @see <a href="https://developer.mozilla.org/en-US/docs/Glossary/Response_header"></a>
     */
    public static Set<String> DEFAULT_RESPONSE_HEADERS_TO_PASS_THROUGH = normalizeHeaders(Set.of(
            HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CACHE_CONTROL,
            HttpHeaders.ETAG,
            HttpHeaders.LINK,
            HttpHeaders.EXPIRES,
            HttpHeaders.LAST_MODIFIED,
            HttpHeaders.RETRY_AFTER
    ));

    public static Set<String> HTTP_METHODS_WHICH_DONT_SUPPORT_BODY = Set.of(
        HttpHead.METHOD_NAME,
        HttpGet.METHOD_NAME
    );

    /**
     * Patterns to look for in headers to pass through
     *
     * @see #passThroughHeaders(HttpEventResponse.HttpEventResponseBuilder, HttpResponse)
     */
    public Set<Pattern> RE_MATCH_HEADERS_PASS_THROUGH = ImmutableSet.of(
            Pattern.compile(normalizeHeader("X-RateLimit.*")));

    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc7230#section-3.2.2">rfc7230</a> "... A recipient
     * MAY combine multiple header fields with the same field name into one "field-name:
     * field-value" pair, without changing the semantics of the message, by appending each
     * subsequent field value to the combined field value in order, separated by a comma."
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
                    this.sanitizer = sanitizerFactory.create(rules,
                            pseudonymizerImplFactory.create(options));
                }
            }
        }
        return this.sanitizer;
    }

    @SneakyThrows
    public HttpEventResponse handle(HttpEventRequest requestToProxy,
            ProcessingContext processingContext) {

        if (requestToProxy.getHttpMethod() == null) {
            log.warning("HTTP method of com.google.cloud.functions.HttpRequest is null !???!");
        }

        // application-level enforcement of HTTPS
        // (NOTE: should be redundant with infrastructure-level configuration)
        if (!requestToProxy.isHttps().orElse(true)) {
            return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_BAD_REQUEST)
                    .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                            ErrorCauses.HTTPS_REQUIRED.name())
                    .bodyString("Requests MUST be sent over HTTPS")
                    .build();
        }

        Optional<HttpEventResponse> healthCheckResponse =
                healthCheckRequestHandler.handleIfHealthCheck(requestToProxy);
        if (healthCheckResponse.isPresent()) {
            return healthCheckResponse.get();
        }



        RequestUrls requestUrls;
        try {
            requestUrls = buildRequestedUrls(requestToProxy);
        } catch (Throwable e) {
            // really shouldn't happen ... parsing one url from another, so would be a bad bug in
            // our canonicalization code for this to go wrong
            log.log(Level.WARNING, "Error parsing  / building request URL", e);

            return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                            ErrorCauses.FAILED_TO_BUILD_URL.name())
                    .bodyString("Error parsing request URL")
                    .build();
        }

        // avoid logging clear URL outside of dev
        URL toLog = envVarsConfigService.isDevelopment() ? requestUrls.getTarget()
                : requestUrls.getOriginal();

        boolean skipSanitization = clientRequestsSkipSanization(requestToProxy);

        HttpEventResponse.HttpEventResponseBuilder builder = HttpEventResponse.builder();

        try {
            this.sanitizer = loadSanitizerRules();
        } catch (Throwable e) {
            log.log(Level.SEVERE, "Error loading sanitizer rules", e);
            return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                    .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                            ErrorCauses.CONFIGURATION_FAILURE.name())
                    .bodyString("Error loading sanitizer rules")
                    .build();
        }

        // build log entry
        String logEntry =
                String.format("%s %s TokenInUrlDecrypted=%b", requestToProxy.getHttpMethod(),
                        URLUtils.relativeURL(toLog), requestUrls.hasDecryptedTokens());
        if (requestToProxy.getClientIp().isPresent()) {
            // client IP is NOT available for direct AWS Lambda calls; only for API Gateway.
            // don't want to put blank/unknown in direct case, so log IP conditionally
            logEntry += String.format(" ClientIP=%s", requestToProxy.getClientIp().get());
        }

        String requestBodyContentType =
                requestToProxy.getHeader(HttpHeaders.CONTENT_TYPE).orElse(ContentType.APPLICATION_JSON.getMimeType());
        String requestBodyContentEncoding =
                requestToProxy.getHeader(HttpHeaders.CONTENT_ENCODING)
                        .orElse(StandardCharsets.UTF_8.name());

        // ensure utf-8 or some otherwise compatible encoding, so we can convert body --> String
        // without fancier decoding atm
        // TODO: support gzip/compressed; and perhaps some non-utf8 encodings??
        if (!isUtf8CompatibleEncoding(requestBodyContentEncoding)) {
            return HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_BAD_REQUEST)
                    .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                            ErrorCauses.INVALID_REQUEST.name())
                    .bodyString(String.format(
                            "Content encoding %s not supported; only UTF-8 compatible encodings are supported (utf-8, ascii, iso-8859-1, us-ascii)",
                            requestBodyContentEncoding))
                    .build();
        }


        String requestBody = Optional.ofNullable(requestToProxy.getBody())
                .map(bytes -> new String(bytes, StandardCharsets.UTF_8))
                .map(StringUtils::trimToNull)
                .orElse(null);

        // return 400 if request body is non-empty, but method is GET or HEAD
        if (HTTP_METHODS_WHICH_DONT_SUPPORT_BODY.contains(requestToProxy.getHttpMethod().toUpperCase())) {
            if (requestBody != null) {
                // rather than have google HttpClient blow up with its own exception, causing 500 from proxy
                return HttpEventResponse.builder()
                        .statusCode(HttpStatus.SC_BAD_REQUEST)
                        .bodyString("Request body is not allowed for GET or HEAD requests")
                        .build();
            }
        }

        if (skipSanitization) {
            log.info(String.format("%s. Skipping sanitization.", logEntry));
        } else if (sanitizer.isAllowed(requestToProxy.getHttpMethod(), requestUrls.getOriginal(),
                requestBodyContentType, requestBody)) {
            log.info(String.format("%s. Rules allowed call.", logEntry));
        } else {
            builder.statusCode(HttpStatus.SC_FORBIDDEN);
            builder.header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                    ErrorCauses.BLOCKED_BY_RULES.name());
            log.warning(String.format("%s. Blocked call by rules %s", logEntry,
                    objectMapper.writeValueAsString(rules)));
            return builder.build();
        }

        com.google.api.client.http.HttpRequest requestToSourceApi;
        try {
            HttpRequestFactory requestFactory = getRequestFactory(requestToProxy);

            HttpContent content = null;

            if (StringUtils.isNotBlank(requestBody)) {
                content = this.reverseRequestBodyTokenization(requestBodyContentType, requestBody);
            }

            // complete hack for Windsurf case, which unlike other sources requires mutation of request body
            // TODO: consider generalizing, if Windsurf lives/continues this authentication interface; and/or we find other sources that require approach
            if (this.sourceAuthStrategy instanceof WindsurfServiceKeyAuthStrategy) {
                content = ((WindsurfServiceKeyAuthStrategy) this.sourceAuthStrategy)
                        .addServiceKeyToRequestBody(content);
            }

            //TODO: always request gziped content from source, even if client didn't request it?

            requestToSourceApi = requestFactory.buildRequest(requestToProxy.getHttpMethod(),
                    new GenericUrl(requestUrls.getTarget()), content);


            // TODO: what headers to forward???
            populateHeadersFromSource(requestToSourceApi, requestToProxy, requestUrls.getTarget());

            // setup request
            requestToSourceApi
                    .setThrowExceptionOnExecuteError(false)
                    .setConnectTimeout(SOURCE_API_REQUEST_CONNECT_TIMEOUT_MS)
                    .setReadTimeout(SOURCE_API_REQUEST_READ_TIMEOUT_MS);

        } catch (IOException e) {
            builder.statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            builder.bodyString("Failed to parse request; review logs");
            builder.header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                    ErrorCauses.CONNECTION_SETUP.name());
            log.log(Level.WARNING, e.getMessage(), e);
            // something like "Error getting access token for service account: 401 Unauthorized POST
            // https://oauth2.googleapis.com/token,"
            log.log(Level.WARNING,
                    "Confirm oauth scopes set in config.yaml match those granted in data source");
            return builder.build();
        } catch (java.util.NoSuchElementException e) {
            // missing config, such as ACCESS_TOKEN
            builder.statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            builder.header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                    ErrorCauses.CONNECTION_SETUP.name());
            log.log(Level.WARNING, e.getMessage(), e);
            return builder.build();
        } catch (ReversibleTokenizationStrategy.InvalidTokenException e) {
            builder.statusCode(HttpStatus.SC_CONFLICT);
            builder.header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                ErrorCauses.TOKENIZED_REQUEST_PARAMETER_INVALID.name());
            log.log(Level.WARNING, e.getMessage(), e);
            return builder.build();
        }


        // check if request is side output only case, if so pass to AsyncApiDataRequestHandler,
        // implementation of which will vary by platform
        if (!processingContext.getAsync() && isAsyncRequested(requestToProxy)) {
            log.info("Requested for async processing");

            // create an async processing context
            ProcessingContext.ProcessingContextBuilder processingContextBuilder =
                    processingContext.toBuilder().async(true);

            ProcessingContext asyncProcessingContext =
                    apiDataOutputUtils.fillOutputContext(processingContextBuilder.build());
            try {
                // TODO: refactor so that we pass requestToSourceApi to the async handler, rather
                // than requestToProxy;
                // requestToProxy has already been authenticated/validated/authorized, so no need to
                // repeat all that
                // problem is that we don't have a direct entry-point into ApiDataRequestHandler
                // using that requestToSourceApi as argument
                asyncApiDataRequestHandler.get().handle(requestToProxy, asyncProcessingContext);
            } catch (Throwable e) {
                log.log(Level.WARNING, "Failure to dispatch async processing of request", e);
                return HttpEventResponse.builder()
                        .statusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR)
                        .header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                                ErrorCauses.ASYNC_HANDLER_DISPATCH.name())
                        .bodyString("Error processing side output only request: " + e.getMessage())
                        .build();
            }
            HttpEventResponse.HttpEventResponseBuilder responseBuilder = HttpEventResponse.builder()
                    .statusCode(HttpStatus.SC_ACCEPTED) // proper response code indicating payload
                                                        // accepted for asynchronous processing
                    .bodyString(objectMapper.writeValueAsString(asyncProcessingContext));


            // TODO: generate host-platform specific signed URL for the async output
            // destination + object
            // - or - create a generic endpoint in the proxy that retrieves the file

            // fill location in accordance with
            // https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Prefer#respond-async
            // RFC : https://www.rfc-editor.org/rfc/rfc7240#section-4.1
            // well, this is s3:// *not* https:// URL, so it is NOT standard compliant
            responseBuilder.header(HttpHeaders.LOCATION,
                    asyncProcessingContext.getAsyncOutputLocation());

            return responseBuilder.build();
        }


        final com.google.api.client.http.HttpResponse sourceApiResponse;
        try {
            // q: add exception handlers for IOExceptions / HTTP error responses, so those retries
            // happen in proxy rather than on Worklytics-side?
            sourceApiResponse = requestToSourceApi.execute();
        } catch (ConnectException e) {
            // connectivity problems
            builder.statusCode(HttpStatus.SC_SERVICE_UNAVAILABLE);
            builder.header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                    ErrorCauses.CONNECTION_TO_SOURCE.name());
            builder.bodyString("Error connecting to source API: " + e.getMessage());
            log.log(Level.SEVERE, "Error connecting to source API: " + e.getMessage(), e);
            return builder.build();
        }


        String proxyResponseContent = "";
        try {
            // return response
            builder.statusCode(sourceApiResponse.getStatusCode());

            ProcessedContent original =
                apiDataOutputUtils.responseAsRawProcessedContent(requestToSourceApi, sourceApiResponse);

            if (apiDataSideOutput.hasRawOutput()) {
                original = original.multiReadableCopy();
                try {
                    apiDataSideOutput.writeRaw(original, processingContext);
                } catch (Output.WriteFailure e) {
                    log.log(Level.WARNING, "Error writing to side output for original content", e);
                    builder.multivaluedHeader(
                        Pair.of(ProcessedDataMetadataFields.WARNING.getHttpHeader(),
                            ErrorCauses.SIDE_OUTPUT_FAILURE_ORIGINAL.name()));
                }
            }

            passThroughHeaders(builder, sourceApiResponse);
            if (isSuccessFamily(sourceApiResponse.getStatusCode())) {
                if (skipSanitization) {
                    if (responseCompressionHandler.isCompressionRequested(requestToProxy)) {
                        proxyResponseContent = original.asCompressed().getContentAsString();
                        builder.header(HttpHeaders.CONTENT_ENCODING, ResponseCompressionHandler.GZIP);
                    } else {
                        proxyResponseContent = original.getContentAsString();
                    }
                } else {
                    ProcessedContent sanitizationResult =
                            sanitize(requestToProxy, requestUrls, original);

                    if (processingContext.getAsync()) {
                        asyncSanitizedDataOutput.get().writeSanitized(sanitizationResult,
                                processingContext);
                    } else {
                        if (sanitizationResult.getContentEncoding() != null) {
                            builder.header(HttpHeaders.CONTENT_ENCODING,
                                    sanitizationResult.getContentEncoding());
                        }
                        proxyResponseContent = sanitizationResult.getContentAsString();
                        sanitizationResult.getMetadata().entrySet()
                                .forEach(e -> builder.header(e.getKey(), e.getValue()));
                    }


                    try {
                        apiDataSideOutputSanitized.writeSanitized(sanitizationResult,
                                processingContext);
                    } catch (Output.WriteFailure e) {
                        log.log(Level.WARNING, "Error writing to side output for sanitized content",
                                e);
                        builder.multivaluedHeader(
                                Pair.of(ProcessedDataMetadataFields.WARNING.getHttpHeader(),
                                        ErrorCauses.SIDE_OUTPUT_FAILURE_SANITIZED.name()));
                    }


                }
            } else {
                // write error, which shouldn't contain PII, directly
                log.log(Level.WARNING, "Source API Error " + original.getContentAsString());

                builder.header(ProcessedDataMetadataFields.ERROR.getHttpHeader(),
                        ErrorCauses.API_ERROR.name());
                proxyResponseContent = original.getContentAsString();

                // q: in async case, perhaps we should write the error to the async output, too, for
                // clarity??? could do it with metadata indicating the error to the caller, so it
                // doesn't wait forever???
                // if versioning is enabled in the bucket, then subsequent successful calls will
                // overwrite the error response
            }

            // only if not async, write content to body of response
            if (!processingContext.getAsync()) {
                builder.body(proxyResponseContent.getBytes(StandardCharsets.UTF_8));
            }

            return builder.build();
        } finally {
            sourceApiResponse.disconnect();
        }
    }

    ProcessedContent sanitize(HttpEventRequest request, RequestUrls requestUrls,
            ProcessedContent originalContent) throws IOException {

        // if the content is `application/gzip', we can't directly sanitize it; we need to uncompress it first
        originalContent = originalContent.isGzipFile() ? uncompressGzipFile(originalContent) : originalContent;

        RESTApiSanitizer sanitizerForRequest = getSanitizerForRequest(request);

        // TODO: a good value for this is probably the content length of the response we received if response Content-Type is JSON
        // maybe 1/10th of content length if ndjson
        int initialBufferSize = 65536; // 64 KB ... big enough for most responses, but not going to push anything OOM

        PipedOutputStream outPipe = new PipedOutputStream();
        InputStream sanitizedContentStream = new PipedInputStream(outPipe, initialBufferSize);
        OutputStream out = responseCompressionHandler.wrapOutputStreamIfRequested(request, outPipe);
        sanitizerForRequest.sanitize(request.getHttpMethod(), requestUrls.getOriginal(), originalContent.getStream(), out);

        String rulesSha = rulesUtils.sha(sanitizerForRequest.getRules());
        log.info("response sanitized with rule set " + rulesSha);

        Map<String, String> metadata = new HashMap<>(originalContent.getMetadata());
        metadata.put(ProcessedDataMetadataFields.RULES_SHA.getMetadataKey(), rulesSha);
        metadata.put(ProcessedDataMetadataFields.PROXY_VERSION.getMetadataKey(),
                HealthCheckRequestHandler.JAVA_SOURCE_CODE_VERSION);
        metadata.put(ProcessedDataMetadataFields.PII_SALT_SHA256.getMetadataKey(),
                healthCheckRequestHandler.piiSaltHash());

        // q: add instance id to the metadata??
        ProcessedContent.ProcessedContentBuilder contentBuilder = ProcessedContent.builder()
            .contentType(originalContent.getContentType())
            .contentCharset(originalContent.getContentCharset())
            .metadata(metadata);

        if (responseCompressionHandler.isCompressionRequested(request)) {
            contentBuilder.contentEncoding(ResponseCompressionHandler.GZIP);
        }

        return contentBuilder.stream(sanitizedContentStream).build();
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
        Optional<PseudonymImplementation> pseudonymImplementation =
                parsePseudonymImplementation(request);
        if (pseudonymImplementation.isPresent()) {
            loadSanitizerRules(); // ensure sanitizer is loaded
            if (!Objects.equals(pseudonymImplementation.get(),
                    sanitizer.getPseudonymizer().getOptions().getPseudonymImplementation())) {
                return sanitizerFactory.create(rules,
                        pseudonymizerImplFactory.create(sanitizer.getPseudonymizer().getOptions()
                                .withPseudonymImplementation(pseudonymImplementation.get())));
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

        // Using original URL to check sanitized rules, as they should match the original URL. It
        // could contain tokenized components.
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
     * @param response - the original response from the upstream API
     */
    @VisibleForTesting
    void passThroughHeaders(HttpEventResponse.HttpEventResponseBuilder responseBuilder,
            com.google.api.client.http.HttpResponse response) {
        Set<String> availableHeaders = normalizeHeaders(response.getHeaders().keySet());


        Sets.intersection(availableHeaders, DEFAULT_RESPONSE_HEADERS_TO_PASS_THROUGH)
                .forEach(h -> responseBuilder.header(h,
                        HEADER_JOINER.join(response.getHeaders().getHeaderStringValues(h))));

        for (String availableHeader : availableHeaders) {
            if (RE_MATCH_HEADERS_PASS_THROUGH.stream()
                    .anyMatch(re -> re.matcher(availableHeader).matches())) {
                responseBuilder.header(availableHeader, HEADER_JOINER
                        .join(response.getHeaders().getHeaderStringValues(availableHeader)));
            }
        }

        if (response.getContentType() != null) {
            responseBuilder.header(normalizeHeader(HttpHeaders.CONTENT_TYPE),
                    response.getContentType());
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
        return headers.stream().map(ApiDataRequestHandler::normalizeHeader)
                .collect(Collectors.toUnmodifiableSet());
    }

    @SneakyThrows
    HttpRequestFactory getRequestFactory(HttpEventRequest request) {

        // q: right idea here? or google-http-client provides factory implementation, but whole
        // point of DI fw is to avoid factories
        //
        HttpTransport transport = httpTransportFactory.create();

        // TODO: changing impl of credentials/initializer should support sources authenticated by
        // something OTHER than a Google Service account
        // eg, OAuth2CredentialsWithRefresh.newBuilder(), etc ..

        // assume that in cloud function env, this will get ours ...
        Optional<String> accountToImpersonate =
                request.getHeader(ControlHeader.USER_TO_IMPERSONATE.getHttpHeader());


        accountToImpersonate.ifPresent(user -> log.info("Impersonating user"));
        // TODO: warn here for Google Workspace connectors, which expect user??

        accountToImpersonate = accountToImpersonate
                .map(s -> pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(s,
                    reversibleTokenizationStrategy));

        Credentials credentials = sourceAuthStrategy.getCredentials(accountToImpersonate);
        HttpCredentialsAdapter initializeWithCredentials = new HttpCredentialsAdapter(credentials);

        // TODO: in OAuth-type use cases, where execute() may have caused token to be refreshed, how
        // do we capture the new one?? ideally do this with listener/handler/trigger in Credential
        // itself, if that's possible

        ComposedHttpRequestInitializer initializer = ComposedHttpRequestInitializer
                .of(initializeWithCredentials, new GzipedContentHttpRequestInitializer("Psoxy"));

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
        uriBuilder.setHost(config.getConfigPropertyOrError(ApiModeConfigProperty.TARGET_HOST));
        URL hostURL = uriBuilder.build().toURL();
        String hostPlusPath = StringUtils.stripEnd(hostURL.toString(), "/") + "/"
                + StringUtils.stripStart(request.getPath(), "/");
        String targetURLString = hostPlusPath;
        if (StringUtils.isNotBlank(request.getQuery().orElse(null))) {
            targetURLString = hostPlusPath + "?" + request.getQuery().get();
        }
        return targetURLString;
    }


    // NOTE: not 'decrypt', as that is only one possible implementation of reversible tokenization
    String reverseTokenizedUrlComponents(String encodedURL) {
        return pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(encodedURL,
                reversibleTokenizationStrategy);
    }

    private void logIfDevelopmentMode(Supplier<String> messageSupplier) {
        if (envVarsConfigService.isDevelopment()) {
            log.info(messageSupplier.get());
        }
    }

    private void populateHeadersFromSource(HttpRequest sourceApiRequest, HttpEventRequest request,
            URL targetUrl) {
        com.google.api.client.http.HttpHeaders headers = sourceApiRequest.getHeaders();

        // seems like Google API HTTP client has a default 'Accept' header with 'text/html,
        // image/gif, image/jpeg, *; q=.2, */*; q=.2' ??
        // MSFT gives weird "{"error":{"code":"InternalServerError","message":"The MIME type
        // 'text/html, image/gif, image/jpeg, *; q=.2, */*; q=.2' requires a '/' character between
        // type and subtype, such as 'text/plain'."}}
        headers.setAccept(ContentType.APPLICATION_JSON.getMimeType());

        sanitizer.getAllowedRequestHeaders(request.getHttpMethod(), targetUrl)
                .ifPresent(i -> i.forEach(h -> request.getHeader(h).ifPresent(headerValue -> {
                    logIfDevelopmentMode(() -> String.format("Header %s included", h));
                    headers.set(h, headerValue);
                })));
    }

    /**
     * Context for processing an API data request
     */
    @NoArgsConstructor // for jackson
    @AllArgsConstructor
    @Builder(toBuilder = true)
    @Data
    public static class ProcessingContext {

        /**
         * request id; does not change for sync v async processing of the same request; so it's the
         * processing request
         * NOTE: a request originally received synchronously, but then processed asynchronously -
         * this value should remain constant
         * it is NOT necessarily the same the request ID of any http request for the current
         * execution; it identifiers the request for the API
         * data itself.
         */
        @NonNull
        String requestId;

        /**
         * when the request was received; used for logging and metrics
         * NOTE: a request originally received synchronously, but then processed asynchronously -
         * this value should remain constant
         */
        @NonNull
        Instant requestReceivedAt;

        /**
         * whether this API data request is processed asynchronously
         */
        @NonNull
        @Builder.Default
        Boolean async = false;

        /**
         * the location for async response output, if any
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String asyncOutputLocation;

        /**
         * the key to which any raw output should be written, if any configure; will be relative to
         * the raw output destination
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String rawOutputKey;

        /**
         * the key to which any sanitized output should be written, if any configured; will be
         * relative to the sanitized output destination; this could be an async output destination,
         * or a side output destination
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String sanitizedOutputKey;

        @Deprecated // use a full builder for this; it generates a requestId, when we might as well
                    // use the platform-generated one
        public static ProcessingContext synchronous(Instant requestReceivedAt) {
            return ProcessingContext.builder()
                    .async(false)
                    .requestId(UUID.randomUUID().toString())
                    .requestReceivedAt(requestReceivedAt)
                    .build();
        }
    }

    boolean isAsyncRequested(HttpEventRequest request) {
        // follows 'proposed' standard RFC 7240 - https://www.rfc-editor.org/rfc/rfc7240.html
        // not actually adopted
        return request.getHeader("Prefer")
                .filter(s -> s.equalsIgnoreCase("respond-async"))
                .isPresent();
    }

    /**
     * Checks if the given encoding is compatible with UTF-8 decoding.
     * Compatible encodings are those where bytes can be safely decoded as UTF-8
     * without data loss or corruption.
     *
     * @param encoding The encoding to check (case-insensitive)
     * @return true if the encoding is UTF-8 compatible, false otherwise
     */
    private boolean isUtf8CompatibleEncoding(String encoding) {
        if (encoding == null) {
            return false;
        }

        String normalizedEncoding = encoding.toLowerCase(Locale.ROOT);

        // UTF-8 variants
        if (normalizedEncoding.equals("utf-8") ||
                normalizedEncoding.equals("utf8") ||
                normalizedEncoding.equals("utf_8")) {
            return true;
        }

        // ASCII variants - ASCII is a subset of UTF-8
        if (normalizedEncoding.equals("ascii") ||
                normalizedEncoding.equals("us-ascii") ||
                normalizedEncoding.equals("us_ascii")) {
            return true;
        }

        // ISO-8859-1 (Latin-1) - compatible with UTF-8 for first 256 code points
        if (normalizedEncoding.equals("iso-8859-1") ||
                normalizedEncoding.equals("iso_8859_1") ||
                normalizedEncoding.equals("latin1") ||
                normalizedEncoding.equals("latin-1")) {
            return true;
        }

        return false;
    }

    /**
     * Reverse tokenization of request body
     *
     * - request body MUST be Content-Type application/json or application/x-www-form-urlencoded
     * - request body encoding MUST be UTF-8 compatible
     *
     *
     * @param contentType
     * @param body
     * @return as ByteArrayContent, for forwarding to source API
     */
    @SneakyThrows
    ByteArrayContent reverseRequestBodyTokenization(@NonNull String contentType, String body) {
        // JSON case: use ObjectMapper to parse request body and map decode ON every string value
        if (contentType.contains(ContentType.APPLICATION_JSON.getMimeType())) {
            JsonNode jsonNode = objectMapper.readTree(body);
            JsonNode transformedNode = applyStringTransformToTree(jsonNode, this::decode);
            String decodedRequestBody = objectMapper.writeValueAsString(transformedNode);

            return new ByteArrayContent(contentType,
                    decodedRequestBody.getBytes(StandardCharsets.UTF_8));
        } else if (contentType.contains(ContentType.APPLICATION_FORM_URLENCODED.getMimeType())) {
            // Form-urlencoded case: use WWWFormCodec to parse request body and map decode ON every
            // value
            List<NameValuePair> nameValuePairs =
                    WWWFormCodec.parse(body, StandardCharsets.UTF_8).stream()
                            .map(pair -> new BasicNameValuePair(pair.getName(),
                                    this.decode(pair.getValue())))
                            .collect(Collectors.toList());

            String decodedRequestBody = WWWFormCodec.format(nameValuePairs, StandardCharsets.UTF_8);

            return new ByteArrayContent(contentType,
                    decodedRequestBody.getBytes(StandardCharsets.UTF_8));
        } else {
            //q: right behavior here?? or should just warn?
            throw new IllegalArgumentException("Unsupported content type: " + contentType);
        }
    }

    String decode(String possiblyEncodedString) {
        return pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(possiblyEncodedString,
                reversibleTokenizationStrategy);
    }

    /**
     * Applies a string transformation function to all textual nodes in a JSON tree.
     * Uses Jackson's JsonNode API to recursively traverse the tree and transform all string values.
     *
     * @param jsonNode The JSON node to transform
     * @param stringTransform Function to apply to each string value
     * @return Transformed JSON node
     */
    @SneakyThrows
    JsonNode applyStringTransformToTree(JsonNode jsonNode,
            Function<String, String> stringTransform) {
        if (jsonNode.isObject()) {
            ObjectNode objectNode = (ObjectNode) jsonNode;
            ObjectNode transformedNode = objectMapper.createObjectNode();

            // Iterate through all fields in the object
            objectNode.fields().forEachRemaining(field -> {
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();

                // Recursively transform child nodes
                transformedNode.set(fieldName,
                        applyStringTransformToTree(fieldValue, stringTransform));
            });
            return transformedNode;
        } else if (jsonNode.isArray()) {
            ArrayNode arrayNode = (ArrayNode) jsonNode;
            ArrayNode transformedArray = objectMapper.createArrayNode();

            // Traverse each element in the array
            for (JsonNode element : arrayNode) {
                transformedArray.add(applyStringTransformToTree(element, stringTransform));
            }
            return transformedArray;
        } else if (jsonNode.isTextual()) {
            // Apply transformation to string values
            String originalValue = jsonNode.asText();
            String transformedValue = stringTransform.apply(originalValue);
            return objectMapper.valueToTree(transformedValue);
        } else {
            // Return leaf nodes unchanged (numbers, booleans, null)
            return jsonNode;
        }
    }

    /**
     * this is to deal with "application-level" compression, rather than http-server level compression
     * eg, the API server is actually serving a gzip file as the resource; not merely gzipping the resource for transport
     * it would be IGNORING any Accepts-Encoding header; bc it knows nothing about the underlying content
     * at the HTTP level AND the API level, it's just data.
     *
     * @param content
     * @return
     * @throws IOException
     */
    ProcessedContent uncompressGzipFile(ProcessedContent content) throws IOException {
        if (content.isGzipFile()) {
            log.info("Decompressing application/gzip response from source API");
            ProcessedContent.ProcessedContentBuilder builder = content.toBuilder()
                .content(null)
                .contentType("application/x-ndjson") // not the correct assumption in all cases ...
                .stream(new GZIPInputStream(content.getStream()));

            return builder.build();
        } else {
            return content;
        }
    }

}
