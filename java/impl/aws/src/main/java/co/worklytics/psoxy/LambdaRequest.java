package co.worklytics.psoxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;


@NoArgsConstructor //for jackson
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
public class LambdaRequest {

    String httpMethod;

    String path;

    @JsonProperty("multiValueQueryStringParameters")
    Map<String, List<String>> queryParameters;

    Map<String, String> pathParameters;

    Map<String, String> stageVariables;

    @JsonProperty("multiValueHeaders")
    Map<String, List<String>> headers;

    String body;
}
