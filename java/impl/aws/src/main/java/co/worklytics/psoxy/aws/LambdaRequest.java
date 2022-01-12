package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@NoArgsConstructor //for jackson
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
public class LambdaRequest implements HttpEventRequest {

    String httpMethod;

    String path;

    @JsonProperty("multiValueQueryStringParameters")
    Map<String, List<String>> queryParameters;

    Map<String, String> pathParameters;

    Map<String, String> stageVariables;

    @JsonProperty("multiValueHeaders")
    Map<String, List<String>> headers;

    String body;

    @Override
    public Optional<String> getQuery() {
        if (this.getQueryParameters() == null) {
            return Optional.empty();
        } else {
            String value = this.getQueryParameters().entrySet().stream()
                .flatMap(parameter -> parameter.getValue().stream().map(v -> parameter.getKey() + "=" + v))
                .collect(Collectors.joining("&"));
            return Optional.ofNullable(StringUtils.trimToNull(value));
        }
    }

    @Override
    public Optional<List<String>> getHeader(String headerName) {
        return Optional.ofNullable(getHeaders().get(headerName));
    }
}
