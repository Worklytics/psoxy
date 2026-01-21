package com.avaulta.gateway.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import com.avaulta.gateway.rules.transforms.Transform;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Singular;
import lombok.With;

@JsonPropertyOrder({"pathRegex", "pathTemplate", "allowedMethods", "allowedQueryParams", "supportedHeaders",
        "transforms"})
@Builder(toBuilder = true)
@With
@AllArgsConstructor // for builder
@NoArgsConstructor // for Jackson
@Getter
public class Endpoint {

    /**
     * path template, eg, /api/v1/{id}/foo/{bar}
     *
     * @see "https://swagger.io/docs/specification/paths-and-operations/"
     *      <p>
     *      if provided, has the effect of pathRegex = "^/api/v1/[^/]+/foo/[^/]+$"
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
     * - if provided, values matched to a named parameter in the pathTemplate will be validated
     * against the schema provided here.
     * - if no schema is provided for a given parameter, any values is permitted.
     * </p>
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Map<String, ParameterSchema> pathParameterSchemas;

    @JsonIgnore
    public Optional<Map<String, ParameterSchema>> getPathParameterSchemasOptional() {
        return Optional.ofNullable(pathParameterSchemas);
    }

    // if provided, only query params in this list will be allowed
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
     * - if a schema is provided for a named parameter here, the value for this parameter in the
     * request will be validated against the schema.
     * - if no scheam provided for the named parameter, any value is permitted.
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
    Set<String> allowedMethods;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Set<String> getAllowedMethods() {
        // our own implementation, so consistently sorted.
        if (allowedMethods == null) {
            return null;
        } else if (allowedMethods instanceof TreeSet) {
            return allowedMethods;
        } else {
            //rely on natural ordering of strings to sort
            this.allowedMethods = new TreeSet<>(allowedMethods);
            return this.allowedMethods;
        }
    }

    @JsonIgnore
    public Optional<Set<String>> getAllowedMethodsOptional() {
        return Optional.ofNullable(getAllowedMethods());
    }

    /**
     * if provided, headers included here will be forwarded through to the source API endpoint if they are present on the request.
     * this can be used for passing a specific header (for example, pagination, limits, etc.) to the request in the source
     *
     * endpoint-matching does NOT take these into account. eg, absence of a header on request will NOT cause the request to not be
     * matched to this endpoint; similarly, presence of a header on a request will NOT cause request to be blocked - the header will
     * simply be dropped.
     *
     * q: should we implement strict request header handling? (block requests will unexpected headers?)
     *
     * NOTE: Using List, as Set is not being serializable in YAML
     */
    @Deprecated // use `allowedRequestHeaders` instead
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Collection<String> allowedRequestHeadersToForward;


    /**
     * if provided, headers included here will be forwarded through to the source API endpoint if they are present on the request.
     * this can be used for passing a specific header (for example, pagination, limits, etc.) to the request in the source
     *
     * endpoint-matching does NOT take these into account. eg, absence of a header on request will NOT cause the request to not be
     * matched to this endpoint; similarly, presence of a header on a request will NOT cause request to be blocked - the header will
     * simply be dropped.
     *
     * q: should we implement strict request header handling? (block requests will unexpected headers?)
     *
     * NOTE: Using List, as Set is not being serializable in YAML
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    Collection<String> allowedRequestHeaders;

    @JsonIgnore
    public Optional<Collection<String>> getAllowedRequestHeaders() {
        return Optional.ofNullable(allowedRequestHeaders);
    }

    @JsonIgnore
    public Optional<Collection<String>> getAllowedRequestHeadersToForward() {
        return Optional.ofNullable(allowedRequestHeadersToForward);
    }

    /**
     * schema used to validate the request body, if any
     * if provided, the request body will be validated against the schema provided here.
     * if no schema is provided, any request body is permitted.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    RequestBodySchema requestBody;

    /**
     * if provided HTTP response will be *filtered* against this schema, with any nodes in the JSON
     * that are not present in the schema being removed.
     *
     * (do not confuse this with plain JSON Schema, which is typically used for validation rather
     * than filtering)
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
                .transforms(
                        this.transforms.stream().map(Transform::clone).collect(Collectors.toList()))
                .allowedQueryParams(
                        this.getAllowedQueryParamsOptional().map(ArrayList::new).orElse(null))
                .pathTemplate(this.pathTemplate)
                .allowedMethods(this.allowedMethods)
                .allowedRequestHeadersToForward(this.allowedRequestHeadersToForward)
                .build();
    }
}
