package co.worklytics.psoxy;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.SuperBuilder;
import lombok.extern.java.Log;

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
public class Rules2 {

    @Singular
    List<Endpoint> endpoints;

    @Builder.Default
    Boolean allowAllEndpoints = false;

    @Builder
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    public static class Endpoint {
        String pathRegex;

        @Singular
        List<Transform> transforms;
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Redaction.class, name = "redact"),
        @JsonSubTypes.Type(value = Pseudonymization.class, name = "pseudonymize") }
    )
    @SuperBuilder
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    @EqualsAndHashCode(callSuper = false)
    public static abstract class Transform {

        String method;

        @Singular
        List<String> jsonPaths;
    }


    @NoArgsConstructor //for jackson
    @SuperBuilder
    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class Redaction extends Transform {

        public static Redaction ofPaths(String... jsonPaths) {
            return Redaction.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }
    }
    @NoArgsConstructor //for jackson
    @SuperBuilder
    @Getter
    public static class EmailHeaderPseudonymization extends Transform {

        public static EmailHeaderPseudonymization ofPaths(String... jsonPaths) {
            return EmailHeaderPseudonymization.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }
    }



    @SuperBuilder
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    public static class Pseudonymization extends Transform {

        @Builder.Default
        Boolean includeOriginal = false;

        //TODO: support this somehow ...
        //String defaultScopeId;

        public static Pseudonymization ofPaths(String... jsonPaths) {
            return Pseudonymization.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }
    }

}
