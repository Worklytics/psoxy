package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import org.apache.http.client.utils.URIBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;


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

    @SneakyThrows
    @Override
    public Optional<String> getQuery() {
        if (this.getQueryParameters() == null || this.getQueryParameters().isEmpty()) {
            return Optional.empty();
        } else {
            URIBuilder uriBuilder = new URIBuilder();
            this.getQueryParameters().entrySet().stream()
                    .forEach(parameter -> parameter.getValue().stream()
                        .forEach(v -> uriBuilder.setParameter(parameter.getKey(), v)));


            return Optional.of(uriBuilder.build().getQuery());
        }
    }

    @Override
    public Optional<List<String>> getHeader(String headerName) {
        return Optional.ofNullable(getHeaders().get(headerName));
    }
}
