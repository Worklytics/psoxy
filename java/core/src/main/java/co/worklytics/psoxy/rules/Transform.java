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
    @JsonSubTypes.Type(value = Transform.FilterTokenByRegex.class, name = "filterTokenByRegex"),
})
@SuperBuilder(toBuilder = true)
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
    @SuperBuilder(toBuilder = true)
    @Getter
    @ToString
    @EqualsAndHashCode(callSuper = true)
    public static class RedactRegexMatches extends Transform {

        /**
         * redaction content matching ANY of these regexes
         */
        @Singular
        List<String> redactions;
    }

    /**
     * transform to tokenize String field by delimiter (if provided), then return any matches against
     * filter regex
     */
    @NoArgsConstructor //for jackson
    @SuperBuilder(toBuilder = true)
    @Getter
    @ToString
    @EqualsAndHashCode(callSuper = true)
    public static class FilterTokenByRegex extends Transform {

        /**
         * token delimiter, if any (if null, token is the whole string)
         */
        @Builder.Default
        String delimiter = "\\s+";

        /**
         * redact content EXCEPT tokens matching at least one of these regexes
         */
        @Singular
        List<String> filters;
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

        /**
         * use if still need original, but also want its pseudonym to be able to match against
         * pseudonymized fields
         *
         * use case: group mailing lists; if they're attendees to an event, the email in that
         * context will be pseudonymized; so when we pull list of groups, we need pseudonyms to
         * match against those, but can also get the original for use in UX/reports, as it's not PII
         */
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        @Builder.Default
        Boolean includeOriginal = false;

        /**
         * whether to include reversible form of pseudonymized value in output
         */
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        @Builder.Default
        Boolean includeReversible = false;

        public static Pseudonymize ofPaths(String... jsonPaths) {
            return Pseudonymize.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }
    }

    @SuperBuilder
    @NoArgsConstructor //for Jackson
    @Getter
    public static class Encrypt extends Transform {

        public static Encrypt defaults() {
            return Encrypt.builder().build();
        }
    }
}
