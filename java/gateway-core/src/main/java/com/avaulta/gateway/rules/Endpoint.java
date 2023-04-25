package com.avaulta.gateway.rules;

import com.avaulta.gateway.rules.transforms.Transform;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@JsonPropertyOrder({"pathRegex", "allowedQueryParams", "transforms"})
@Builder(toBuilder = true)
@With
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
public class Endpoint {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    String pathRegex;

    /**
     * path template, eg, /api/v1/{id}/foo/{bar}
     *
     * @see "https://swagger.io/docs/specification/paths-and-operations/"
     *
     * if provided, has the effect of pathRegex = "^/api/v1/[^/]+/foo/[^/]+$"
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String pathTemplate;


    /**
     * if parameters appearing in pathTemplate appear in this, must validate against this to match
     * the endpoint
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<ParameterSpec> pathParameterConstraints;

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
            .build();
    }


}
