package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import com.google.cloud.functions.HttpRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

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
}
