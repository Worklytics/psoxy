package co.worklytics.psoxy.aws.request;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.google.common.base.Splitter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RequiredArgsConstructor(staticName = "of")
public class APIGatewayV1ProxyEventRequestAdapter implements co.worklytics.psoxy.gateway.HttpEventRequest {


    final @NonNull APIGatewayProxyRequestEvent event;

    private Map<String, String> caseInsensitiveHeaders;

    @Override
    public String getPath() {
        return StringUtils.prependIfMissing(event.getPath(), "/");
    }

    @Override
    public Optional<String> getQuery() {
        if (event.getQueryStringParameters() == null || event.getQueryStringParameters().isEmpty()) {
            return Optional.empty();
        } else {
            return Optional.of(event.getQueryStringParameters().entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&")));
        }
    }

    @Override
    public Optional<String> getHeader(String headerName) {
        return Optional.ofNullable(getCaseInsensitiveHeaders().get(headerName.toLowerCase()));
    }

    @Override
    public Optional<List<String>> getMultiValueHeader(String headerName) {
        return getHeader(headerName.toLowerCase()).map(s -> Splitter.on(',').splitToList(s));
    }

    @Override
    public String getHttpMethod() {
        if (event.getRequestContext().getHttpMethod() == null) {
            //q: better exception to throw here???
            throw new IllegalStateException("Psoxy expects API Gateway V1 REST API proxy payload here. If using API Gateway V2 or Lambda function, please set the handler as co.worklytics.psoxy.Handler");
        }

        return event.getRequestContext().getHttpMethod();
    }

    @Override
    public byte[] getBody() {
        return StringUtils.isNotBlank(event.getBody()) ? event.getBody().getBytes() : null;
    }

    @Override
    public String prettyPrint() {
        return event.toString();
    }

    /**
     * @return view of Headers with lower-case names
     *
     * this is kinda defensive; while AWS seems to pass headers in lower-case, GCP does not. (or
     * at least docs, and prior practice, suggest they do not). But really we probably want to
     * presume case-insensitivity, giving clients leeway in how they send headers.
     *
     * additionally, we don't want to couple ourselves to (undocumented?) AWS behavior. and we have
     * both customer implementations directly exposing lambdas via URLs as well as putting behind an
     * API Gateway; and potentially people could put these behind API Gateway V1 or V2, as well as
     * define their own API gateway request mappings ... so we want to be as flexible as possible
     * rather than presume that headers get converted to lower case through all these potential
     * logical paths.
     */
    private Map<String, String> getCaseInsensitiveHeaders() {
        if (caseInsensitiveHeaders == null) {
            caseInsensitiveHeaders = event.getHeaders().entrySet().stream()
                .collect(Collectors.toMap(
                    entry -> entry.getKey().toLowerCase(),
                    Map.Entry::getValue
                ));
        }
        return caseInsensitiveHeaders;
    }
}
