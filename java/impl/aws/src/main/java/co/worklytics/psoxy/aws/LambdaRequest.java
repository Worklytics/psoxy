package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * java POJO equivalent to JSON-encoded HTTP event passed to AWS lambda
 *
 * NOTE: in practice this does NOT seem to match the AWS SDK HTTPEvent pojo
 * @see com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent
 *
 * So we've relied on our own mapping of the fields they show in:
 * @see "https://docs.aws.amazon.com/lambda/latest/dg/services-apigateway.html#apigateway-example-event"
 *
 *
 *
 */
//NOTE: in practice, this does NOT seem to match

@NoArgsConstructor //for jackson
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@ToString
public class LambdaRequest implements HttpEventRequest {

    String httpMethod;

    String path;


    @Override
    public String getPath() {
        // proxy is the name of the parameter in AWS API Gateway "{proxy+}", so whatever comes here
        // is the original path requested
        String proxy = pathParameters.get("proxy");
        return StringUtils.prependIfMissing(proxy,"/");
    }

    @JsonProperty("multiValueQueryStringParameters")
    Map<String, List<String>> queryParameters;

    Map<String, String> pathParameters;

    Map<String, String> stageVariables;

    @JsonProperty("multiValueHeaders")
    Map<String, List<String>> headers;

    String body;

    RequestContext requestContext;

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

    @NoArgsConstructor
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RequestContext {

        String stage;
    }
}
