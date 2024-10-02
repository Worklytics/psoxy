package co.worklytics.psoxy.aws.request;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.google.common.base.Splitter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Adapter for the APIGatewayV2HTTPEvent to the interface the {@link co.worklytics.psoxy.gateway.impl.CommonRequestHandler}
 * understands
 * Lambda calls use this very same adapter, but some fields differ as the nature of the service is
 * different.
 * https://docs.aws.amazon.com/lambda/latest/dg/urls-invocation.html#urls-payloads
 */
@RequiredArgsConstructor
@Log
public class APIGatewayV2HTTPEventRequestAdapter implements HttpEventRequest {

    @NonNull
    final APIGatewayV2HTTPEvent event;

    private Map<String, String> caseInsensitiveHeaders;

    @Override
    public String getPath() {

        // unclear whether this will exist, but if it does, use it
        // q: is this only a v1 thing?? or only a v2 thing??
        // - I can't find a v2 proxy payload example
        // - AWS docs seem to have a v1 exxample, but not v2 https://docs.aws.amazon.com/lambda/latest/dg/services-apigateway.html#apigateway-proxy
        // - https://github.com/aws/aws-sam-cli/issues/3654 - shows it, but not filled with a value that
        //   seems consistent with other properties
        if (event.getPathParameters() != null && event.getPathParameters().containsKey("proxy")) {
            return StringUtils.prependIfMissing(event.getPathParameters().get("proxy"), "/");
        }

        //remove stage portion from path, if any
        //  - for Lambda URL deployments, we expect 'stage' to be '$default', and not match any portion
        //    of rawPath; this will be no-op
        //  - for API Gateway deployments, this should strip the stage
        String rawPath = event.getRawPath().replace("/" + event.getRequestContext().getStage(), "");


        // in API gateway deployments, multiple lambdas are behind single gateway with routes like
        //  - ANY /gcal/{proxy+} --> psoxy-gcal lambda
        //  - ANY /gdrive/{proxy+} --> psoxy-gcal-settings lambda
        // eg ANY /gcal/{proxy+} -> /gcal/...

        //NOTE: we're *assuming* routeKey of structure "ANY /gcal" for route "ANY /gcal/{proxy+}",
        // but haven't found an example or documentation that explicitly states this

        if (event.getRequestContext().getRouteKey() != null &&
            event.getRequestContext().getRouteKey().contains(" ")) {
            String route = event.getRequestContext().getRouteKey().split(" ")[1];

            rawPath = rawPath.replace(route, "");
        }

        //don't think needed
        return StringUtils.prependIfMissing(rawPath, "/");
    }

    @Override
    public Optional<String> getQuery() {
        return Optional.ofNullable(event.getRawQueryString());
    }

    @Override
    public Optional<String> getHeader(@NonNull String headerName) {
        // Seems APIGatewayV2HTTPEvent has the headers lower-case
        return Optional.ofNullable(getCaseInsensitiveHeaders().get(headerName.toLowerCase()));
    }

    @Override
    public Optional<List<String>> getMultiValueHeader(@NonNull String headerName) {
        return getHeader(headerName.toLowerCase()).map(s -> Splitter.on(',').splitToList(s));
    }

    @Override
    public String getHttpMethod() {
        if (event.getRequestContext().getHttp() == null) {
            //q: better exception to throw here???
            throw new IllegalStateException("Psoxy expects an AWS API Gateway V2 HTTP Proxy payload here.  If your lambda is triggered by API Gateway V1 instead of a V2 API gateway or a Lambda Function URL, please use co.worklytics.psoxy.APIGatewayV1Handler as the handler class");
        }

        return event.getRequestContext().getHttp().getMethod();
    }

    @Override
    @SneakyThrows
    public byte[] getBody() {
        return StringUtils.isNotBlank(event.getBody()) ? event.getBody().getBytes() : null;
    }

    @Override
    public String prettyPrint() {
        return event.toString();
    }

    @Override
    public Optional<String> getClientIp() {
        // standard: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For
       return Optional.ofNullable(event.getHeaders().get("X-Forwarded-For"));
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
