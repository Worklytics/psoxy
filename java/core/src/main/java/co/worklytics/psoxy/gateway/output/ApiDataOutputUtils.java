package co.worklytics.psoxy.gateway.output;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.gateway.*;
import co.worklytics.psoxy.gateway.impl.ApiDataRequestHandler;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpHeaders;

import javax.inject.Inject;
import javax.inject.Provider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@AllArgsConstructor(onConstructor_ = {@Inject})
public class ApiDataOutputUtils {

    /**
     * keys for metadata that will be added to API Data output objects.
     *
     * in all of these cases, except API_HOST, the values will be as follows:
     *    - original (raw) case, will be the ACTUAL value sent with the request to the source API
     *    - sanitized case, will be the value as it was sent to the proxy, which is presumably sanitized to reduce exposure of sensitive data
     */
    public enum OutputObjectMetadata {

        /**
         * the host to which the request was sent, eg, api.saas-tool.com
         */
        API_HOST,

        /**
         * the HTTP method of the request, eg, GET, POST, ...
         */
        HTTP_METHOD,

        /**
         * the path of the request
         */
        PATH,

        /**
         * query string of the request, if any
         */
        QUERY_STRING,

        /**
         * base64-encoded body of the request, if any (eg, for POST requests)
         */
        REQUEST_BODY,

        //q: include response info??
        // status code? other headers?
        ;
    }

    ConfigService config;
    Provider<UUID> uuidProvider;
    Base64.Encoder base64encoder;

    public String buildRawOutputKey(ApiDataRequestHandler.ProcessingContext processingContext) {
        String date = LocalDate.ofInstant(processingContext.getRequestReceivedAt(), ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return date + "/" + Optional.ofNullable(processingContext.getRequestId()).orElse(uuidProvider.get().toString());
    }

    public String buildSanitizedOutputKey(ApiDataRequestHandler.ProcessingContext processingContext) {
        String date = LocalDate.ofInstant(processingContext.getRequestReceivedAt(), ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return date + "/" +  Optional.ofNullable(processingContext.getRequestId()).orElse(uuidProvider.get().toString());
    }

    public ApiDataRequestHandler.ProcessingContext fillOutputContext(ApiDataRequestHandler.ProcessingContext processingContext) {
        ApiDataRequestHandler.ProcessingContext.ProcessingContextBuilder builder = processingContext.toBuilder()
            .asyncOutputLocation(
                config.getConfigPropertyAsOptional(ApiModeConfigProperty.ASYNC_OUTPUT_DESTINATION)
                .map(s -> s + "/" + this.buildSanitizedOutputKey(processingContext))
                .orElse(null));

        if (config.getConfigPropertyAsOptional(ProxyConfigProperty.SIDE_OUTPUT_ORIGINAL).isPresent()) {
            builder.rawOutputKey(this.buildRawOutputKey(processingContext));
        }
        return builder.build();
    }


    /**
     * wrap api response produced by request as {@link ProcessedContent}
     *
     * in effect, it's the request that's been processed.
     *
     *
     * @param sourceApiRequest request to the source API
     * @param sourceApiResponse response from the source API
     * @return ProcessedContent containing the response content, metadata, etc.
     * @throws IOException
     */
    public ProcessedContent responseAsRawProcessedContent(HttpRequest sourceApiRequest,
                                                          HttpResponse sourceApiResponse) throws IOException {
        ProcessedContent.ProcessedContentBuilder builder = ProcessedContent.builder()
            .contentType(sourceApiResponse.getContentType())
            .contentCharset(sourceApiResponse.getContentCharset());

        try (InputStream stream = sourceApiResponse.getContent()) {
            builder.content(stream.readAllBytes());
        }

        Map<String, String> metadata = this.buildRawMetadata(sourceApiRequest);


        //not sure this will work; are we certain to be able to consume HttpContent after request has been sent?
        if (sourceApiRequest.getContent() != null
            && sourceApiRequest.getContent().getLength() > 0) {
            // if the request has a body, add it to metadata
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            sourceApiRequest.getContent().writeTo(out);
            String body = base64encoder.encodeToString(out.toByteArray());
            metadata.put(OutputObjectMetadata.REQUEST_BODY.name(), body);
        }

        builder.metadata(metadata);



        return builder.build();
    }

    Map<String, String> buildRawMetadata(HttpRequest sourceApiRequest) {
        HashMap<String, String> metadata = new HashMap<>();
        metadata.put(ApiDataOutputUtils.OutputObjectMetadata.API_HOST.name(), sourceApiRequest.getUrl().getHost());

        // split rawPath into path and query string
        String path = normalizePath(sourceApiRequest.getUrl().getRawPath());
        if (!StringUtils.isEmpty(path)) {
            metadata.put(ApiDataOutputUtils.OutputObjectMetadata.PATH.name(), path);
        }

        Pair<String, String> splitPathAndQuery = splitPathAndQuery(sourceApiRequest.getUrl().buildRelativeUrl());

        if (splitPathAndQuery.getRight() != null) {
            metadata.put(ApiDataOutputUtils.OutputObjectMetadata.QUERY_STRING.name(), canonicalQuery(Optional.of(splitPathAndQuery.getRight())));
        }

        metadata.put(ApiDataOutputUtils.OutputObjectMetadata.HTTP_METHOD.name(), sourceApiRequest.getRequestMethod());

        return metadata;
    }

    private Pair<String, String> splitPathAndQuery(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) {
            return  Pair.of(null, null);
        }
        String[] parts = rawPath.split("\\?", 2);
        String path = normalizePath(parts[0]);
        String query = parts.length > 1 ? parts[1] : null;
        return Pair.of(path, query);
    }

    /**
     * builds metadata for output object based on request, which intended for writing to GCS/S3 metadata
     *
     * (Azure Blob Storage metadata support is more limited, so likely this will not work there)
     *
     * does NOT enforce platform-specific constraints on metadata keys/values; we leave it to the platform
     * implementation to truncate/warn/encode as desired.
     *
     * @param requestToProxy
     * @return
     */
    Map<String, String> buildMetadata(HttpEventRequest requestToProxy) {

        Map<String, String> metadata = new HashMap<>();

        requestToProxy.getHeaders().entrySet().stream()
            .filter(entry -> this.isParameterHeader(entry.getKey()))
            .forEach(entry -> metadata.put(entry.getKey(), String.join(",", entry.getValue())));

        metadata.put(OutputObjectMetadata.HTTP_METHOD.name(), requestToProxy.getHttpMethod());
        metadata.put(OutputObjectMetadata.PATH.name(), requestToProxy.getPath());
        requestToProxy.getQuery().ifPresent(query -> metadata.put(OutputObjectMetadata.QUERY_STRING.name(), query));

        Optional.ofNullable(requestToProxy.getBody())
            .map(base64encoder::encodeToString)
            .ifPresent(body -> metadata.put(OutputObjectMetadata.REQUEST_BODY.name(), body));

        return metadata;
    }


    final static Set<String> HEADERS_TO_IGNORE = Set.of(
        HttpHeaders.HOST,
        HttpHeaders.USER_AGENT,
        HttpHeaders.ACCEPT,
        HttpHeaders.ACCEPT_ENCODING,
        HttpHeaders.AUTHORIZATION, // not relevant to response contents, and may contain sensitive info
        HttpHeaders.CONTENT_LENGTH,
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

    private String normalizePath(String rawPath) {
        // Remove leading/trailing slashes and URL-decode
        if (rawPath == null || rawPath.isEmpty()) return "";
        return Arrays.stream(rawPath.split("/"))
            .filter(s -> !s.isEmpty())
            .map(part -> URLDecoder.decode(part, StandardCharsets.UTF_8))
            .collect(Collectors.joining("/"));
    }

}
