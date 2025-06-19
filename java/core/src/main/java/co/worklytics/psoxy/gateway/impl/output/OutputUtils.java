package co.worklytics.psoxy.gateway.impl.output;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.HealthCheckRequestHandler;
import co.worklytics.psoxy.gateway.output.*;
import com.google.common.annotations.VisibleForTesting;
import lombok.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * utility methods for working with side output
 *
 * - eg, to create a canonical key for the response to be stored in the side output
 * - or to build metadata for the side output
 */
@NoArgsConstructor(onConstructor_ = {@Inject})
@Singleton
public class OutputUtils {

    @AllArgsConstructor
    enum ObjectMetadataKey {
        PII_SALT_SHA256("PII-Salt-Sha256"),
        PROXY_VERSION("Psoxy-Version"),
        USER_TO_IMPERSONATE("User-To-Impersonate"),
        ;

        private final String key;
    }

    @Inject
    HostEnvironment hostEnvironment;
    @Inject
    HealthCheckRequestHandler healthCheckRequestHandler;
    @Inject
    ConfigService configService;
    @Inject
    Provider<NoOutput> noSideProvider;
    @Inject
    Set<OutputFactory<?>> outputFactories;
    @Inject
    OutputToSideOutputAdapterFactory outputToSideOutputAdapterFactory;


    /**
     * get a canonical key for the response to be stored in the side output
     *
     * @param request
     * @return a string key that should be deterministic for the same/equivalent requests; as well as legal
     *       for use as a key in S3/GCS ...
     */
    public String canonicalResponseKey(HttpEventRequest request) {
        //q: do we need to consider response headers? YES!!!!
        // - eg, could be API responses that are NOT cacheable, so in theory we should append some of the headers that denote this
        //     eg, etag, Expires,

        // note: if this exceeds 1024 bytes, we should probably hash it again

       return request.getHttpMethod()
            + "_"
            + request.getHeader("Host").orElse("")
            + "/"
            + normalizePath(request.getPath())
            + hashQueryAndHeaders(request).map(s -> "_" + s).orElse("");
    }

    public  Map<String, String>  buildMetadata(HttpEventRequest request) {
        Map<String, String> metadata = new HashMap<>();

        metadata.put(ObjectMetadataKey.PROXY_VERSION.key, HealthCheckRequestHandler.JAVA_SOURCE_CODE_VERSION);

        // to aid in detecting if pseudonyms are consistent across objects
        // - eg, data can only be linked if/when this value is consistent
        metadata.put(ObjectMetadataKey.PII_SALT_SHA256.key, healthCheckRequestHandler.piiSaltHash());

        request.getHeaders().entrySet().stream()
            .filter(entry -> this.isParameterHeader(entry.getKey()))
            .forEach(entry -> metadata.put(entry.getKey(), String.join(",", entry.getValue())));

        return metadata;
    }

    /**
     * helper method to interpret config, as to whether there's a side output for the given content stage or not
     *
     * @param processedDataStage the stage of processed data to be written to the side output
     */
    public ApiDataSideOutput forStage(ProcessedDataStage processedDataStage) {

        ProxyConfigProperty configProperty = switch (processedDataStage) {
            case ORIGINAL -> ProxyConfigProperty.SIDE_OUTPUT_ORIGINAL;
            case SANITIZED -> ProxyConfigProperty.SIDE_OUTPUT_SANITIZED;
        };

        Output outputToAdapt =  configService.getConfigPropertyAsOptional(configProperty)
            .map(OutputLocationImpl::of)
            .map(this::createOutputForLocation)
            .map(output -> (Output) CompressedOutputWrapper.wrap((Output) output))
            .orElseGet(noSideProvider::get);

        return outputToSideOutputAdapterFactory.create(outputToAdapt);
    }

    public <T extends Output> T forWebhooks() {
        return createOutputForLocation(locationForWebhooks());
    }

    public <T extends Output> T forWebhookQueue() {
        return createOutputForLocation(locationForWebhookQueue());
    }


    @VisibleForTesting
    OutputLocation locationForWebhooks() {
        return configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.WEBHOOK_OUTPUT)
            .map(OutputLocationImpl::of).orElseThrow(() -> new IllegalStateException("No side output configured for webhooks"));
    }

    @VisibleForTesting
    OutputLocation locationForWebhookQueue() {
        return configService.getConfigPropertyAsOptional(WebhookCollectorModeConfigProperty.WEBHOOK_BATCH_OUTPUT)
            .map(OutputLocationImpl::of)
            .orElseThrow(() -> new IllegalStateException("No side output configured for webhook queue"));
    }


    private <T extends Output> T createOutputForLocation(OutputLocation outputLocation) {
        OutputFactory<?> outputFactory =  outputFactories.stream()
            .filter(factory -> factory.supports(outputLocation))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No output factory found for location: " + outputLocation));

        return (T) outputFactory.create(outputLocation);
    }



    // Ensure the path prefix ends with a slash, if non-empty
    public static String formatObjectPathPrefix(String rawPathPrefix) {
        String trimmedPath = StringUtils.trimToEmpty(rawPathPrefix);
        return (trimmedPath.endsWith("/") || StringUtils.isEmpty(trimmedPath)) ? trimmedPath : trimmedPath + "/";
    }

    private String normalizePath(String rawPath) {
        // Remove leading/trailing slashes and URL-decode
        if (rawPath == null || rawPath.isEmpty()) return "";
        return Arrays.stream(rawPath.split("/"))
            .filter(s -> !s.isEmpty())
            .map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8))
            .collect(Collectors.joining("/"));
    }

    private Optional<String> hashQueryAndHeaders(HttpEventRequest httpEventRequest) {

        String canonicalQuery = canonicalQuery(httpEventRequest.getQuery());

        // add any headers that are relevant to response contents
        String canonicalHeaders = httpEventRequest.getHeaders().entrySet().stream()
            .filter(entry -> isParameterHeader(entry.getKey()))
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .sorted()
            .collect(Collectors.joining(";"));

         return Optional.ofNullable(StringUtils.trimToNull(canonicalQuery + canonicalHeaders))
             .map(DigestUtils::md5Hex);
    }

    final static Set<String> HEADERS_TO_IGNORE = Set.of(
        "Host",
        "User-Agent",
        "Accept",
        "Accept-Encoding",
        "Authorization", // not relevant to response contents, and may contain sensitive info
        "Content-Length",
        "Forwarded",
        "traceparent",
        "X-Forwarded-For",
        "x-cloud-trace-context",
        "X-Forwarded-Proto"
    ).stream().map(String::toLowerCase).collect(Collectors.toSet());

    /**
     * a header that parameterizes response content.
     *  (eg, would expect multiple possible valid values for the headers for requests through same connection,
     *   with expectation that those requests result in different responses)
     *
     *  eg, stuff like 'User-Agent' is NOT expected to result in different responses, so is not a parameter header.
     *
     * @param headerName to test
     * @return true if the header is a parameter header, false otherwise
     */
    boolean isParameterHeader(String headerName) {
        String caseInsensitiveHeaderName = headerName.toLowerCase(Locale.ROOT);
        return ControlHeader.USER_TO_IMPERSONATE.getHttpHeader().toLowerCase().equals(caseInsensitiveHeaderName)
        || (!HEADERS_TO_IGNORE.contains(caseInsensitiveHeaderName) &&
            !caseInsensitiveHeaderName.startsWith("x-psoxy-") && // ignore control headers (other than USER_TO_IMPERSONATE)
            !caseInsensitiveHeaderName.startsWith("x-amz-") && // ignore AWS-specific headers
            !caseInsensitiveHeaderName.startsWith("x-goog-")); // ignore GCP-specific headers
    }

    String canonicalQuery(Optional<String> rawQuery) {
        if (rawQuery.isEmpty()) return "";

        List<String> sortedParams = Arrays.stream(rawQuery.get().split("&"))
            .map(s -> s.split("=", 2))
            .map(pair -> {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = pair.length > 1 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
                return key + "=" + value;
            })
            .sorted()
            .collect(Collectors.toList());

     return String.join("&", sortedParams);
    }

}
