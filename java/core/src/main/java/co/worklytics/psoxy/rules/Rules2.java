package co.worklytics.psoxy.rules;


import co.worklytics.psoxy.rules.slack.PrebuiltSanitizerRules;
import com.avaulta.gateway.rules.Endpoint;
import com.avaulta.gateway.rules.JsonSchemaFilter;
import com.avaulta.gateway.rules.transforms.Transform;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import lombok.*;
import lombok.extern.java.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serial;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Builder(toBuilder = true)
@Log
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode
@JsonIgnoreProperties({"defaultScopeIdForSource"})
@JsonPropertyOrder({"allowAllEndpoints", "endpoints", "defaultScopeIdForSource"})
@JsonInclude(JsonInclude.Include.NON_NULL) //NOTE: despite name, also affects YAML encoding
public class Rules2 implements RESTRules {

    @Serial
    private static final long serialVersionUID = 1L;

    @Singular
    List<Endpoint> endpoints;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @Deprecated //will be dropped in v0.5
    @Builder.Default
    Boolean allowAllEndpoints = false;

    Map<String, JsonSchemaFilter> definitions;

    /**
     * root definitions, to be in scope across all endpoints
     *
     * @return definitions, in a TreeMap so that serialization is deterministic
     */
    public Map<String, JsonSchemaFilter> getDefinitions() {
        if (definitions != null && !(definitions instanceof TreeMap)) {
            definitions = new TreeMap<>(definitions);
        }
        return definitions;
    }


    /**
     * add endpoints from other ruleset to this one
     * @param endpointsToAdd to be added
     * @return new ruleset based on this one, but with added endpoints
     */
    public Rules2 withAdditionalEndpoints(Endpoint... endpointsToAdd) {
        Rules2Builder builder = this.toBuilder();
        Arrays.stream(endpointsToAdd).forEach(builder::endpoint);
        return builder.build();
    }

    public Rules2 withAdditionalEndpoints(List<Endpoint> endpointsToAdd) {
        Rules2Builder builder = this.toBuilder();
        endpointsToAdd.forEach(builder::endpoint);
        return builder.build();
    }

    public Rules2 withTransformByEndpointTemplate(String pathTemplate, Transform... transforms) {
        Rules2 clone = this.clone();
        Optional<Endpoint> matchedEndpoint = clone.getEndpoints().stream()
            .filter(endpoint -> Objects.equals(endpoint.getPathTemplate(), pathTemplate))
            .findFirst();

        Endpoint endpoint = matchedEndpoint
            .orElseThrow(() -> new IllegalArgumentException("No endpoint found for pathTemplate: " + pathTemplate));

        endpoint.setTransforms(
            Stream.concat(endpoint.getTransforms().stream(), Arrays.stream(transforms))
                .collect(Collectors.toList()));

        return clone;
    }

    // deprecated; use withTransformByEndpointTemplate instead, as we want to move to pathTemplates in future
    public Rules2 withTransformByEndpoint(String pathRegex, Transform... transforms) {
        Rules2 clone = this.clone();
        Optional<Endpoint> matchedEndpoint = clone.getEndpoints().stream()
            .filter(endpoint -> Objects.equals(endpoint.getPathRegex(), pathRegex))
            .findFirst();

        Endpoint endpoint = matchedEndpoint
            .orElseThrow(() -> new IllegalArgumentException("No endpoint found for pathRegex: " + pathRegex));

        endpoint.setTransforms(
            Stream.concat(endpoint.getTransforms().stream(), Arrays.stream(transforms))
                .collect(Collectors.toList()));

        return clone;
    }

    public Rules2 clone() {
        Rules2 clone = this.toBuilder()
            .clearEndpoints()
            .endpoints(this.endpoints.stream().map(Endpoint::clone).collect(Collectors.toList()))
            .build();
        return clone;
    }

    //TODO: fix YAML serialization with something like
    // https://stackoverflow.com/questions/55878770/how-to-use-jsonsubtypes-for-polymorphic-type-handling-with-jackson-yaml-mapper

    static ObjectMapper mapper = new YAMLMapper();

    public static synchronized Rules2 load(String path) {
        try (InputStream is = Rules2.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return mapper.readerFor(Rules2.class).readValue(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load rules from: " + path, e);
        }
    }

}
