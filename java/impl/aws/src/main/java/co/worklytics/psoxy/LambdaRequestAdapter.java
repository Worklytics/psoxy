package co.worklytics.psoxy;

import co.worklytics.psoxy.gateway.ProxyRequestAdapter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class LambdaRequestAdapter implements ProxyRequestAdapter<LambdaRequest> {

    @Override
    public String getPath(LambdaRequest request) {
        return request.getPath();
    }

    @Override
    public Optional<String> getQuery(LambdaRequest request) {
        if (request.getQueryParameters() == null) {
            return Optional.empty();
        } else {
            String value = request.getQueryParameters().entrySet().stream()
                .flatMap(parameter -> parameter.getValue().stream().map(v -> parameter.getKey() + "=" + v))
                .collect(Collectors.joining("&"));
            return Optional.ofNullable(StringUtils.trimToNull(value));
        }
    }

    @Override
    public Optional<List<String>> getHeader(LambdaRequest request, String headerName) {
        return Optional.ofNullable(request.getHeaders().get(headerName));
    }
}
