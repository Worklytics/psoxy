package co.worklytics.psoxy.rules;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.extern.java.Log;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Builder(toBuilder = true)
@Log
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL) //NOTE: despite name, also affects YAML encoding
public class Rules2 implements RuleSet, Serializable {


    private static final long serialVersionUID = 1L;

    /**
     * scopeId to set for any identifiers parsed from source that aren't email addresses
     *
     * NOTE: can be overridden by config, in case you're connecting to an on-prem / private instance
     * of the source and you don't want it's identifiers to be treated as under the default scope
     */
    @Getter
    String defaultScopeIdForSource;

    @Singular
    List<Endpoint> endpoints;

    @Builder.Default
    Boolean allowAllEndpoints = false;


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

    public Rules2 withTransformByEndpoint(String pathRegex, Transform... transforms) {
        Rules2 clone = this.clone();
        Optional<Endpoint> matchedEndpoint = clone.getEndpoints().stream()
            .filter(endpoint -> endpoint.getPathRegex().equals(pathRegex))
            .findFirst();

        Endpoint endpoint = matchedEndpoint
            .orElseThrow(() -> new IllegalArgumentException("No endpoint found for pathRegex: " + pathRegex));

        endpoint.transforms = new ArrayList<>(endpoint.getTransforms());

        Arrays.stream(transforms).forEach(endpoint.transforms::add);

        return clone;
    }

    public Rules2 clone() {
        Rules2 clone = this.toBuilder()
            .clearEndpoints()
            .endpoints(this.endpoints.stream().map(Endpoint::clone).collect(Collectors.toList()))
            .build();
        return clone;
    }


    @JsonPropertyOrder(alphabetic = true)
    @Builder(toBuilder = true)
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    public static class Endpoint {

        String pathRegex;

        @JsonInclude(value=JsonInclude.Include.NON_EMPTY)
        @Singular
        List<Transform> transforms = new ArrayList<>();

        @Override
        public Endpoint clone() {
            return this.toBuilder()
                .clearTransforms()
                .transforms(this.transforms.stream().map(Transform::clone).collect(Collectors.toList()))
                .build();
        }
    }


    //TODO: fix YAML serialization with something like
    // https://stackoverflow.com/questions/55878770/how-to-use-jsonsubtypes-for-polymorphic-type-handling-with-jackson-yaml-mapper




}
