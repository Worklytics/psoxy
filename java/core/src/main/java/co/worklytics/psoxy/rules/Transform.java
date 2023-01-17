package co.worklytics.psoxy.rules;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.avaulta.gateway.rules.SchemaRuleUtils;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaFormat;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Transform.Redact.class, name = "redact"),
    @JsonSubTypes.Type(value = Transform.RedactRegexMatches.class, name = "redactRegexMatches"),
    @JsonSubTypes.Type(value = Transform.Pseudonymize.class, name = "pseudonymize"),
    @JsonSubTypes.Type(value = Transform.PseudonymizeEmailHeader.class, name = "pseudonymizeEmailHeader"),
    @JsonSubTypes.Type(value = Transform.FilterBySchema.class, name = "filterBySchema"),
    @JsonSubTypes.Type(value = Transform.FilterTokenByRegex.class, name = "filterTokenByRegex"),
    @JsonSubTypes.Type(value = Transform.Tokenize.class, name = "tokenize"),
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
    @SuperBuilder(toBuilder = true)
    @Getter
    @EqualsAndHashCode(callSuper = true)
    public static class Redact extends Transform {

        public static Redact ofPaths(String... jsonPaths) {
            return Redact.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }

        public Redact clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .clearFields()
                .fields(new ArrayList<>(this.fields))
                .build();
        }
    }

    @NoArgsConstructor //for jackson
    @SuperBuilder(toBuilder = true)
    @Getter
    @ToString
    @EqualsAndHashCode(callSuper = true)
    public static class RedactRegexMatches extends Transform {

        /**
         * redact content matching ANY of these regexes
         */
        @Singular
        List<String> redactions;

        public RedactRegexMatches clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .clearFields()
                .fields(new ArrayList<>(this.fields))
                .clearRedactions()
                .redactions(new ArrayList<>(this.redactions))
                .build();
        }
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

        public FilterTokenByRegex clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .clearFields()
                .fields(new ArrayList<>(this.fields))
                .clearFilters()
                .filters(new ArrayList<>(this.filters))
                .build();
        }
    }

    /**
     * **alpha support**
     *
     * transform to tokenize String field by delimiter (if provided), then return any matches against
     * filter regex
     */
    @NoArgsConstructor //for jackson
    @SuperBuilder(toBuilder = true)
    @Getter
    @ToString
    @EqualsAndHashCode(callSuper = true)
    public static class FilterBySchema extends Transform {


        /**
         * filter content by schema (Eg, redact any properties not in schema; or any values that
         * don't match types specified in schema)
         */
        SchemaRuleUtils.JsonSchema schema;

        public FilterBySchema clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .build();
        }
    }



    @NoArgsConstructor //for jackson
    @SuperBuilder(toBuilder = true)
    @Getter
    public static class PseudonymizeEmailHeader extends Transform {

        public static PseudonymizeEmailHeader ofPaths(String... jsonPaths) {
            return PseudonymizeEmailHeader.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }

        public PseudonymizeEmailHeader clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .clearFields()
                .fields(new ArrayList<>(this.fields))
                .build();
        }
    }

    @SuperBuilder(toBuilder = true)
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

        /**
         * how to encode to the resulting pseudonym
         */
        @JsonInclude(JsonInclude.Include.NON_DEFAULT) //doesn't work for enums ...
        @Builder.Default
        PseudonymEncoder.Implementations encoding = PseudonymEncoder.Implementations.JSON;

        public static Pseudonymize ofPaths(String... jsonPaths) {
            return Pseudonymize.builder().jsonPaths(Arrays.asList(jsonPaths)).build();
        }

        public Pseudonymize clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .clearFields()
                .fields(new ArrayList<>(this.fields))
                .build();
        }
    }

    @SuperBuilder(toBuilder = true)
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    public static class Tokenize extends Transform {

        /**
         * if provided, only group within matched by this regex will be tokenized
         *
         * example usage: .regex("^https://graph.microsoft.com/(.*)$") will tokenize the path
         * of a MSFT graph URL (prev/next links in paged endpoints), which may be useful if path
         * might contain PII or something like that
         *
         * HUGE CAVEAT: as of Aug 2022, reversing encapsulated tokens BACK to their original values
         * will work if and only if token is bounded by non-base64-urlencoded character
         */
        @Nullable
        String regex;

        //NOTE: always format to URL-safe
        public Tokenize clone()  {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .clearFields()
                .fields(new ArrayList<>(this.fields))
                .build();
        }
    }


    //TODO: can we implement abstract portion of this somehow??
    public abstract Transform clone();

}
