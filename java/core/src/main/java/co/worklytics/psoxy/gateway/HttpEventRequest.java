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

    Optional<List<String>> getHeader(String headerName);

}
