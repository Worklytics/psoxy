package co.worklytics.psoxy.gateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * an invocation of a serverless function based on HTTP request
 *
 *
 */
public interface HttpEventRequest {

    // "de-facto" standard: https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-For
    String HTTP_HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    // "de-facto" standard:  https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Forwarded-Proto
    String HTTP_HEADER_X_FORWARDED_PROTO = "X-Forwarded-Proto";


    String getPath();

    Optional<String> getQuery();

    Optional<String> getHeader(String headerName);

    Optional<List<String>> getMultiValueHeader(String headerName);

    Map<String, List<String>> getHeaders();

    String getHttpMethod();

    byte[] getBody();

    default String prettyPrint() {
        return "Not implemented";
    }

    /**
     * @return IP of the client making the request, if known.
     * When derived from {@link #HTTP_HEADER_X_FORWARDED_FOR}, that header is a comma-separated
     * chain ordered left-to-right: the first address is the original client, with each subsequent
     * address added by a proxy on the path.
     */
    Optional<String> getClientIp();

    /**
     * @return whether original protocol of request is HTTPS, if known
     */
    Optional<Boolean> isHttps();

    /**
     * @return  the platform-specific representation of the request, e.g. an AWS API Gateway event, a GCP Cloud Function event, etc.
     */
    Object getUnderlyingRepresentation();
}
