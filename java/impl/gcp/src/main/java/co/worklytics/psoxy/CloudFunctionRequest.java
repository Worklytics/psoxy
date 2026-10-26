package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import com.google.cloud.functions.HttpRequest;
import com.google.common.base.Splitter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.http.HttpHeaders;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor(staticName = "of")
public class CloudFunctionRequest implements HttpEventRequest {

    /**
     * Negative offset from the end of {@code X-Forwarded-For}. {@code -1} is the last hop.
     * Unset means {@link #CLIENT_X_FORWARDED_FOR_POSITION_DIRECT}.
     */
    static final String ENV_CLIENT_X_FORWARDED_FOR_POSITION = "CLIENT_X_FORWARDED_FOR_POSITION";

    /** Direct Cloud Run URL. The last hop is the address Cloud Run appended. */
    static final int CLIENT_X_FORWARDED_FOR_POSITION_DIRECT = -1;

    /**
     * External Application Load Balancer. The load balancer appends the client it observed and then
     * the forwarding-rule address, so the client is the second-to-last hop.
     */
    static final int CLIENT_X_FORWARDED_FOR_POSITION_EXTERNAL_ALB = -2;

    @NonNull
    final HttpRequest request;

    /** Negative offset from the end of {@code X-Forwarded-For}. */
    final int clientXForwardedForPosition;

    static int clientXForwardedForPosition(String value) {
        if (value == null || value.isBlank()) {
            return CLIENT_X_FORWARDED_FOR_POSITION_DIRECT;
        }
        int position;
        try {
            position = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Invalid " + ENV_CLIENT_X_FORWARDED_FOR_POSITION + ": '" + value
                    + "'. Use a negative offset from the end of X-Forwarded-For, such as -1 (direct) or -2 (external load balancer).",
                e);
        }
        if (position >= 0) {
            throw new IllegalArgumentException(
                ENV_CLIENT_X_FORWARDED_FOR_POSITION + " must be a negative offset from the end of X-Forwarded-For (for example -1 or -2), not '"
                    + value + "'.");
        }
        return position;
    }

    private Map<String, List<String>> caseInsensitiveHeaders;

    private byte[] body;

    // path as received from the HTTP runtime (normalization happens in ApiDataRequestHandler)
    @Override
    public String getPath() {
        return request.getPath();
    }

    @Override
    public Optional<String> getQuery() {
        return request.getQuery();
    }

    /**
     * @return view of Headers with lower-case names
     *
     * this is kinda defensive; while AWS seems to pass headers in lower-case, GCP does not. (or
     * at least docs, and prior practice, suggest they do not). But really we probably want to
     * presume case-insensitivity, giving clients leeway in how they send headers.
     */
    private Map<String, List<String>> getCaseInsensitiveHeaders() {
        if (caseInsensitiveHeaders == null) {
            caseInsensitiveHeaders = request.getHeaders().entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> entry.getKey().toLowerCase(),
                    Map.Entry::getValue
                ));
        }
        return caseInsensitiveHeaders;
    }

    @Override
    public Optional<String> getHeader(@NonNull String headerName) {
        return Optional.ofNullable(getCaseInsensitiveHeaders().get(headerName.toLowerCase()))
            .map(values -> values.get(0));
    }

    @Override
    public Optional<List<String>> getMultiValueHeader(@NonNull String headerName) {
        return Optional.ofNullable(getCaseInsensitiveHeaders().get(headerName.toLowerCase()));
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return request.getHeaders();
    }

    @Override
    public String getHttpMethod() {
        return request.getMethod();
    }

    @Override
    @SneakyThrows
    public byte[] getBody() {
        if (body == null) {
            body = request.getInputStream() != null ? request.getInputStream().readAllBytes() : null;
        }

        return body;
    }

    @Override
    public String prettyPrint() {
        return request.toString();
    }

    @Override
    public Optional<String> getClientIp() {
        return getHeader(HttpEventRequest.HTTP_HEADER_X_FORWARDED_FOR)
            .flatMap(value -> clientIpAtPosition(value, clientXForwardedForPosition));
    }

    private static Optional<String> clientIpAtPosition(String xForwardedFor, int positionFromEnd) {
        List<String> hops = Splitter.on(',').trimResults().omitEmptyStrings().splitToList(xForwardedFor);
        int index = hops.size() + positionFromEnd;
        if (index < 0 || index >= hops.size()) {
            return Optional.empty();
        }
        return Optional.of(hops.get(index));
    }

    @Override
    public Optional<Boolean> isHttps() {
        return getHeader(HttpEventRequest.HTTP_HEADER_X_FORWARDED_PROTO)
            .map(proto -> proto.equalsIgnoreCase("https"));
    }

    @Override
    public Object getUnderlyingRepresentation() {
        return request;
    }


    public List<String> getWarnings() {
        List<String> warnings = new LinkedList<>();

        //Compression-related
        // clients should request compressed content to reduce running costs of proxy instances;
        // warn if they aren't
        // https://cloud.google.com/appengine/docs/legacy/standard/go111/how-requests-are-handled#:~:text=For%20responses%20that%20are%20returned,HTML%2C%20CSS%2C%20or%20JavaScript.
        warnIfHeaderAbsentOrMissingGzip(HttpHeaders.ACCEPT_ENCODING).ifPresent(warnings::add);
        warnIfHeaderAbsentOrMissingGzip(HttpHeaders.USER_AGENT).ifPresent(warnings::add);

        return warnings;
    }

    private Optional<String> warnIfHeaderAbsentOrMissingGzip(String headerName) {
        if (this.getHeader(headerName).isPresent()) {
            if (!this.getHeader(headerName).get().contains("gzip")) {
                return Optional.of(headerName + " header found, but does not include 'gzip'; this is recommended to enable compression");
            }
        } else {
            return Optional.of("No " + headerName + " header found; should include 'gzip' to enable compression");
        }
        return Optional.empty();
    }

}
