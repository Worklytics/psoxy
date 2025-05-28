package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import com.google.cloud.functions.HttpRequest;
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
     * see "https://cloud.google.com/functions/docs/configuring/env-var"
     */
    enum RuntimeEnvironmentVariables {
        K_SERVICE,
    }

    public static String getFunctionName() {
        return System.getenv(RuntimeEnvironmentVariables.K_SERVICE.name());
    }

    @NonNull
    final HttpRequest request;

    private Map<String, List<String>> caseInsensitiveHeaders;


    private byte[] body;

    @Override
    public String getPath() {
        return request.getPath()
            .replace(getFunctionName() + "/", "");
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
        return Optional.ofNullable(request.getHeaders().get(HttpEventRequest.HTTP_HEADER_X_FORWARDED_FOR))
            .map(values -> values.get(0));
    }

    @Override
    public Optional<Boolean> isHttps() {
        return Optional.ofNullable(request.getHeaders().get(HttpEventRequest.HTTP_HEADER_X_FORWARDED_PROTO))
            .map(values -> values.get(0).equals("https"));
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
