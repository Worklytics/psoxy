package co.worklytics.psoxy.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Arrays;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Transform.Redact.class, name = "redact"),
    @JsonSubTypes.Type(value = Transform.RedactRegexMatches.class, name = "redactRegexMatches"),
    @JsonSubTypes.Type(value = Transform.Pseudonymize.class, name = "pseudonymize"),
    @JsonSubTypes.Type(value = Transform.PseudonymizeEmailHeader.class, name = "pseudonymizeEmailHeader"),
})
@SuperBuilder
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode(callSuper = false)
public abstract class Transform {

    //NOTE: this is filled for JSON, but for YAML a YAML-specific type syntax is used:
    // !<pseudonymize>
    // Jackson YAML can still *read* yaml-encoded transform with `method: "pseudonymize"`
    @JsonInclude(JsonInclude.Include.NON_NULL)
    String method;

    /**
     * specifies JSON paths within content that identify value to be transformed
     * <p>
     * each value that matches each of the paths will be passed to transform's function as a
     * separate invocation.
     * <p>
     * supported for JSON
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Singular
    List<String> jsonPaths;

    /**
     * specifies fields within content that identify value to be transformed
     * <p>
     * supported for CSV
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Singular
    List<String> fields;

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
    @EqualsAndHashCode(callSuper = true)
    public static class RedactRegexMatches extends Transform {

        String regex;

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
