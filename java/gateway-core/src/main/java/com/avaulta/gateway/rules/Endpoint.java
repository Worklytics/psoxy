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

    /**
     * path template, eg, /api/v1/{id}/foo/{bar}
     *
     * @see "https://swagger.io/docs/specification/paths-and-operations/"
     * <p>
     * if provided, has the effect of pathRegex = "^/api/v1/[^/]+/foo/[^/]+$"
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String pathTemplate;

    /**
     * a regex to match against the HTTP path of the request
     * <p>
     * where possible, use `pathTemplate` instead unless you need some functionality here.
     * </p>
     * <p>
     * our hope is to replace this with `pathTemplate` + `pathParameterSchemas` in the future, as
     * the logic is a bit more straightforward and more closely matches the OpenAPI spec standard.
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String pathRegex;


    /**
     * schemas used to validate path parameters
     *
     * <p>
     *   - if provided, values matched to a named parameter in the pathTemplate will be validated
     *     against the schema provided here.
     *   - if no schema is provided for a given parameter, any values is permitted.
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, ParameterSchema> pathParameterSchemas;

    @JsonIgnore
    public Optional<Map<String, ParameterSchema>> getPathParameterSchemasOptional() {
        return Optional.ofNullable(pathParameterSchemas);
    }

    //if provided, only query params in this list will be allowed
    @JsonInclude(JsonInclude.Include.NON_NULL)
    List<String> allowedQueryParams;

    @JsonIgnore
    public Optional<List<String>> getAllowedQueryParamsOptional() {
        return Optional.ofNullable(allowedQueryParams);
    }

    /**
     * schemas used to validate query parameters
     *
     * <p>
     *   - if a schema is provided for a named parameter here, the value for this parameter in the
     *     request will be validated against the schema.
     *   - if no scheam provided for the named parameter, any value is permitted.
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, QueryParameterSchema> queryParamSchemas;

    @JsonIgnore
    public Optional<Map<String, QueryParameterSchema>> getQueryParamSchemasOptional() {
        return Optional.ofNullable(queryParamSchemas);
    }

    /**
     * if provided, only HTTP methods in this list will be allowed (eg, GET, HEAD, etc)
     *
     * if omitted, any HTTP method is permitted.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Set<String> allowedMethods;

    @JsonIgnore
    public Optional<Set<String>> getAllowedMethods() {
        return Optional.ofNullable(allowedMethods);
    }

    /**
     * if provided, headers included here will be passed through to the endpoint
     * this can be used for passing a specific header (for example, pagination, limits, etc.)
     * to the request in the source
     * NOTE: Using List, as Set is not being serializable in YAML
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Collection<String> allowedRequestHeadersToForward;

    @JsonIgnore
    public Optional<Collection<String>> getAllowedRequestHeadersToForward() {
        return Optional.ofNullable(allowedRequestHeadersToForward);
    }

    /**
     * if provided HTTP response will be *filtered* against this schema, with any nodes in the JSON
     * that are not present in the schema being removed.
     *
     * (do not confuse this with plain JSON Schema, which is typically used for validation rather
     *  than filtering)
     *
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    JsonSchemaFilter responseSchema;

    @JsonIgnore
    public Optional<JsonSchemaFilter> getResponseSchemaOptional() {
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
