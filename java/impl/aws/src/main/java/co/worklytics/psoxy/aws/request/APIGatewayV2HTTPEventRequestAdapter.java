package co.worklytics.psoxy.aws.request;

import co.worklytics.psoxy.gateway.HttpEventRequest;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.google.common.base.Splitter;
import com.google.common.collect.Maps;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * Adapter for the APIGatewayV2HTTPEvent to the interface the {@link co.worklytics.psoxy.gateway.impl.CommonRequestHandler}
 * understands
 */
@RequiredArgsConstructor
@Log
public class APIGatewayV2HTTPEventRequestAdapter implements HttpEventRequest {

    @NonNull final APIGatewayV2HTTPEvent event;

    @Override
    public String getPath() {
        return StringUtils.prependIfMissing(event.getPathParameters().get("proxy"),"/");
    }

    @Override
    public Optional<String> getQuery() {
        return Optional.ofNullable(event.getRawQueryString());
    }

    @Override
    public Optional<String> getHeader(String headerName) {
        // Seems APIGatewayV2HTTPEvent has the headers lower-case
        return Optional.ofNullable(event.getHeaders().get(headerName.toLowerCase()));
    }

    @Override
    public Optional<List<String>> getMultiValueHeader(String headerName) {
        return getHeader(headerName.toLowerCase()).map( s -> Splitter.on(',').splitToList(s));
    }

    @Override
    public String getHttpMethod() {
        return event.getRequestContext().getHttp().getMethod();
    }

    @Override
    public String prettyPrint() {
        return event.toString();
    }
}
