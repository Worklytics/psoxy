package co.worklytics.psoxy.gateway.output;

import co.worklytics.psoxy.ControlHeader;
import co.worklytics.psoxy.gateway.HttpEventRequest;
import co.worklytics.psoxy.gateway.ProcessedContent;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class OutputToApiDataSideOutputAdapter implements ApiDataSideOutput {


    final Output wrappedOutput;

    @AssistedInject
    public OutputToApiDataSideOutputAdapter(@Assisted Output wrappedOutput) {
        this.wrappedOutput = wrappedOutput;
    }

    @Override
    public void write(HttpEventRequest request, ProcessedContent content) throws IOException {
        // exploits mutability of content.metadata ...
        buildMetadata(request).forEach(content.getMetadata()::put);

        wrappedOutput.write(canonicalResponseKey(request), content);
    }

    /**
     * get a canonical key for the response to be stored in the side output
     *
     * @param request the request for which the response is being written
     * @return a string key that should be deterministic for the same/equivalent requests; as well as legal
     *       for use as a key in S3/GCS ...
     */
    String canonicalResponseKey(HttpEventRequest request) {
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

    Map<String, String> buildMetadata(HttpEventRequest request) {
        Map<String, String> metadata = new HashMap<>();

        request.getHeaders().entrySet().stream()
            .filter(entry -> this.isParameterHeader(entry.getKey()))
            .forEach(entry -> metadata.put(entry.getKey(), String.join(",", entry.getValue())));

        return metadata;
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

}
