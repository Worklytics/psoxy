package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ProxyRequestAdapter;
import com.google.cloud.functions.HttpRequest;
import lombok.NonNull;

import java.util.List;
import java.util.Optional;

public class CloudFunctionRequestAdapter implements ProxyRequestAdapter<HttpRequest> {

    /**
     * see "https://cloud.google.com/functions/docs/configuring/env-var"
     */
    enum RuntimeEnvironmentVariables {
        K_SERVICE,
    }

    @Override
    public String getPath(HttpRequest request) {
        return request.getPath()
            .replace(System.getenv(RuntimeEnvironmentVariables.K_SERVICE.name()) + "/", "");
    }

    @Override
    public Optional<String> getQuery(HttpRequest request) {
        return request.getQuery();
    }

    @Override
    public Optional<List<String>> getHeader(@NonNull HttpRequest request, @NonNull String headerName) {
        return Optional.ofNullable(request.getHeaders().get(headerName));
    }
}


