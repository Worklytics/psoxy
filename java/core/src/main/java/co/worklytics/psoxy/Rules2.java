package co.worklytics.psoxy;


import co.worklytics.psoxy.rules.Rules;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.java.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Builder(toBuilder = true)
@Log
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL) //NOTE: despite name, also affects YAML encoding
public class Rules2 implements Rules, Serializable {


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


    @JsonPropertyOrder(alphabetic = true)
    @Builder
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    public static class Endpoint {
        String pathRegex;

        @JsonInclude(value=JsonInclude.Include.NON_EMPTY)
        @Singular
        List<Transform> transforms = new ArrayList<>();
    }

    //TODO: fix YAML serialization with something like
    // https://stackoverflow.com/questions/55878770/how-to-use-jsonsubtypes-for-polymorphic-type-handling-with-jackson-yaml-mapper

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Redact.class, name = "redact"),
        @JsonSubTypes.Type(value = Pseudonymize.class, name = "pseudonymize") }
    )
    @SuperBuilder
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    @EqualsAndHashCode(callSuper = false)
    public static abstract class Transform {

        //NOTE: this is filled for JSON, but for YAML a YAML-specific type syntax is used:
        // !<pseudonymize>
        // Jackson YAML can still *read* yaml-encoded transform with `method: "pseudonymize"`
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String method;

        @Singular
        List<String> jsonPaths;
    }


    @NoArgsConstructor //for jackson
    @SuperBuilder
    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class Redact extends Transform {

        public static Redact ofPaths(String... jsonPaths) {
            return Redact.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }
    }
    @NoArgsConstructor //for jackson
    @SuperBuilder
    @Getter
    public static class PseudonymizeEmailHeader extends Transform {

        public static PseudonymizeEmailHeader ofPaths(String... jsonPaths) {
            return PseudonymizeEmailHeader.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }
    }



    @SuperBuilder
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    public static class Pseudonymize extends Transform {

        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        @Builder.Default
        Boolean includeOriginal = false;

        //TODO: support this somehow ...
        //String defaultScopeId;

        public static Pseudonymize ofPaths(String... jsonPaths) {
            return Pseudonymize.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }
    }

}
