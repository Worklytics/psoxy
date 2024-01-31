package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;

import java.util.Map;

/**
 *
 * q: split interface and implementation? What's the point?
 *
 */
@Value
@Builder(toBuilder = true)
@ToString
public class HttpEventResponse {

    int statusCode;

    @Singular
    Map<String, String> headers;

    String body;
}
