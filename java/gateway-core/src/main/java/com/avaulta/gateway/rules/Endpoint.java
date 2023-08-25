package com.avaulta.gateway.rules;

import com.avaulta.gateway.rules.transforms.Transform;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;

import java.util.*;
import java.util.stream.Collectors;

@JsonPropertyOrder({"pathRegex", "pathTemplate", "allowedQueryParams", "supportedHeaders", "transforms"})
@Builder(toBuilder = true)
@With
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
public class Endpoint {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String pathRegex;

    /**
     * ALPHA FEATURE
     * path template, eg, /api/v1/{id}/foo/{bar}
     *
     * @see "https://swagger.io/docs/specification/paths-and-operations/"
     * <p>
     * if provided, has the effect of pathRegex = "^/api/v1/[^/]+/foo/[^/]+$"
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String pathTemplate;

    //if provided, only query params in this list will be allowed
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> allowedQueryParams;

    //TODO: add conditionally allowed query parameters? (eg, match value against a regex?)

    @JsonIgnore
    public Optional<List<String>> getAllowedQueryParamsOptional() {
        return Optional.ofNullable(allowedQueryParams);
    }


    //if provided, only http methods in this list will be allowed
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Set<String> allowedMethods;

    @JsonIgnore
    public Optional<Set<String>> getAllowedMethods() {
        return Optional.ofNullable(allowedMethods);
    }

    //if provided, headers provided will be pass-through to the endpoint
    // this can be used for passing a specific header (for example, pagination, limits, etc.)
    // to the request in the source
    // NOTE: Using List, as Set is not being serializable in YAML
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Collection<String> allowedRequestHeadersToForward;

    @JsonIgnore
    public Optional<Collection<String>> getAllowedRequestHeaderesToForward() {
        return Optional.ofNullable(allowedRequestHeadersToForward);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonSchemaFilterUtils.JsonSchemaFilter responseSchema;

    @JsonIgnore
    public Optional<JsonSchemaFilterUtils.JsonSchemaFilter> getResponseSchemaOptional() {
        return Optional.ofNullable(responseSchema);
    }

    @Setter
    @JsonInclude(value = JsonInclude.Include.NON_EMPTY)
    @Singular
    List<Transform> transforms = new ArrayList<>();

    @Override
    public Endpoint clone() {
        return this.toBuilder()
                .clearTransforms()
                .transforms(this.transforms.stream().map(Transform::clone).collect(Collectors.toList()))
                .allowedQueryParams(this.getAllowedQueryParamsOptional().map(ArrayList::new).orElse(null))
                .pathTemplate(this.pathTemplate)
                .allowedMethods(this.allowedMethods)
                .allowedRequestHeadersToForward(this.allowedRequestHeadersToForward)
                .build();
    }
}