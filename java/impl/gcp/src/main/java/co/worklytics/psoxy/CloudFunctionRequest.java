package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import com.google.cloud.functions.HttpRequest;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Optional;

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

    @Override
    public Optional<String> getHeader(@NonNull String headerName) {
        return request.getFirstHeader(headerName);
    }

    @Override
    public Optional<List<String>> getMultiValueHeader(String headerName) {
        return Optional.ofNullable(request.getHeaders().get(headerName));
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
