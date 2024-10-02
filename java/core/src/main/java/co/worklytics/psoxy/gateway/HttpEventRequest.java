package co.worklytics.psoxy.gateway;

import java.util.List;
import java.util.Optional;

/**
 * an invocation of a serverless function based on HTTP request
 *
 *
 */
public interface HttpEventRequest {

    String getPath();

    Optional<String> getQuery();

    Optional<String> getHeader(String headerName);

    Optional<List<String>> getMultiValueHeader(String headerName);

    String getHttpMethod();

    byte[] getBody();

    default String prettyPrint() {
        return "Not implemented";
    }

    /**
     * @return IP of the client making the request, if known
     */
    Optional<String> getClientIp();

}
