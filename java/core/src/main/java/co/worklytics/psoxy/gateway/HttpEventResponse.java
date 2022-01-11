package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

/**
 *
 * q: split interface and implementation? What's the point?
 *
 */
@Value
@Builder
public class HttpEventResponse {

    int statusCode;

    @Singular
    List<Pair<String, String>> headers;

    String body;
}
