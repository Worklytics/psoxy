package co.worklytics.psoxy.gateway;

import lombok.Builder;
import lombok.Singular;
import lombok.ToString;
import lombok.Value;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;
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

    @Singular
    List<Pair<String, String>> multivaluedHeaders;

    public Map<String, List<String>> getMultivaluedHeaders() {
        return multivaluedHeaders.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    Pair::getLeft,
                    java.util.stream.Collectors.mapping(Pair::getRight, java.util.stream.Collectors.toList())
                )
            );
    }

}
