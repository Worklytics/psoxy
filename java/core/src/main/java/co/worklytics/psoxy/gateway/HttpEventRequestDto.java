package co.worklytics.psoxy.gateway;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;

/**
 * a JSON-serializable representation of an HttpEventRequest
 */
@Builder
@Getter
@Setter
@NoArgsConstructor // for Jackson
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HttpEventRequestDto implements HttpEventRequest {

    private String path;
    @JsonProperty("query")
    private String query;
    private String httpMethod;
    private byte[] body;

    @JsonProperty("clientIp")
    private String clientIp;

    private Map<String, List<String>> headers;
    private Boolean https;

    @JsonIgnore
    @Override
    public Optional<String> getClientIp() {
        return Optional.ofNullable(clientIp);
    }

    @JsonIgnore
    @Override
    public Optional<String> getQuery() {
        return Optional.ofNullable(this.query);
    }

    @Override
    public Optional<String> getHeader(@NonNull String headerName) {
        return Optional.ofNullable(headers.get(headerName))
            .flatMap(list -> list.stream().findFirst());
    }

    @Override
    public Optional<List<String>> getMultiValueHeader(@NonNull String headerName) {
        return Optional.ofNullable(headers.get(headerName));
    }

    @JsonIgnore
    @Override
    public Object getUnderlyingRepresentation() {
    /**
     * This DTO does not wrap any underlying representation, so we return the DTO itself.
     * This avoids UnsupportedOperationException and provides a safe default.
     */
    @JsonIgnore
    @Override
    public Object getUnderlyingRepresentation() {
        return this;
    }

    @Override
    public Optional<Boolean> isHttps() {
        return Optional.ofNullable(https);
    }


    /**
     * @param request to copy
     * @return shallow, JSON-serializable copy of request
     */
    public static HttpEventRequestDto copyOf(HttpEventRequest request) {
        return HttpEventRequestDto.builder()
            .path(request.getPath())
            .query(request.getQuery().orElse(null))
            .httpMethod(request.getHttpMethod())
            .body(request.getBody())
            .clientIp(request.getClientIp().orElse(null))
            .headers(request.getHeaders())
            .https(request.isHttps().orElse(null))
            .build();
    }
}
