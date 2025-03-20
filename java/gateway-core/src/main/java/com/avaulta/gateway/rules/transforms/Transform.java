package com.avaulta.gateway.rules.transforms;

import com.avaulta.gateway.pseudonyms.PseudonymEncoder;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "method")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Transform.Redact.class, name = "redact"),
    @JsonSubTypes.Type(value = Transform.RedactRegexMatches.class, name = "redactRegexMatches"),
    @JsonSubTypes.Type(value = Transform.RedactExceptPhrases.class, name = "redactExceptPhrases"),
    @JsonSubTypes.Type(value = Transform.RedactExceptSubstringsMatchingRegexes.class, name = "redactExceptSubstringsMatchingRegexes"),
    @JsonSubTypes.Type(value = Transform.Pseudonymize.class, name = "pseudonymize"),
    @JsonSubTypes.Type(value = Transform.PseudonymizeEmailHeader.class, name = "pseudonymizeEmailHeader"),
    @JsonSubTypes.Type(value = Transform.FilterTokenByRegex.class, name = "filterTokenByRegex"),
    @JsonSubTypes.Type(value = Transform.Tokenize.class, name = "tokenize"),
    @JsonSubTypes.Type(value = Transform.TextDigest.class, name = "textDigest"),
    @JsonSubTypes.Type(value = Transform.PseudonymizeRegexMatches.class, name = "pseudonymizeRegexMatches"),
    @JsonSubTypes.Type(value = HashIp.class, name = "hashIp"),
    @JsonSubTypes.Type(value = EncryptIp.class, name = "encryptIp")
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
     *
     * @Deprecated - CSV now uses columnar rules
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Singular
    @Deprecated
    List<String> fields;


    /**
     * A JsonPath whether apply the transformation if matches the expected value
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    @Builder.Default
    String applyOnlyWhen = null;

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

    //beta
    @NoArgsConstructor //for jackson
    @SuperBuilder(toBuilder = true)
    @Getter
    @ToString
    @EqualsAndHashCode(callSuper = true)
    public static class RedactExceptPhrases extends Transform {

        /**
         * redact portions of content that do NOT match these phases; if multiple match, will build
         * CSV of all matching phrases present in field
         */
        @Singular
        List<String> allowedPhrases;

        public RedactExceptPhrases clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .clearFields()
                .fields(new ArrayList<>(this.fields))
                .clearAllowedPhrases()
                .allowedPhrases(new ArrayList<>(this.allowedPhrases))
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

    @NoArgsConstructor //for jackson
    @SuperBuilder(toBuilder = true)
    @Getter
    @ToString
    @EqualsAndHashCode(callSuper = true)
    public static class RedactExceptSubstringsMatchingRegexes extends Transform {

        /**
         * redact content unless matches at least one of these regexes
         *
         * if multiple match, content matched by the first exception regex is preserved
         */
        @Singular
        List<String> exceptions;

        // q: why not make all the exceptions case-insensitive by default??

        // why not make boundary encapsulation the default??

        public RedactExceptSubstringsMatchingRegexes clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .clearFields()
                .fields(new ArrayList<>(this.fields))
                .clearExceptions()
                .exceptions(new ArrayList<>(this.exceptions))
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

    public interface PseudonymizationTransform {

        Boolean getIncludeOriginal();

        Boolean getIncludeReversible();

    }

    @SuperBuilder(toBuilder = true)
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    public static class Pseudonymize extends Transform implements PseudonymizationTransform {

        /**
         * use if still need original, but also want its pseudonym to be able to match against
         * pseudonymized fields
         *
         * use case: group mailing lists; if they're attendees to an event, the email in that
         * context will be pseudonymized; so when we pull list of groups, we need pseudonyms to
         * match against those, but can also get the original for use in UX/reports, as it's not PII
         *
         * NOT compatible with URL_SAFE_TOKEN encoding (Validator checks for this)
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
         * if provided, only values at this json path(es) will be pseudonymized
         * if has a capture group, only portion of value matched by regex captured by first group
         *
         *
         * portion of content matching first capture group in regex will be
         * pseudonymized; rest preserved;
         *
         * if regex does NOT match content, content is left as-is (not pseudonymized)
         *   q: better to redact?
         *
         * Use case: format preserving pseudonymization, namely Microsoft Graph API encodes email
         * aliases as smtp:mailbox@domain.com or SMTP:mailbox@domain.com, depending on secondary
         * or primary; we want to pseudonymize only the actual mailbox portion
         *
         * @since 0.4.36
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String onlyIfRegex;

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
    public static class PseudonymizeRegexMatches extends Transform implements PseudonymizationTransform  {
        /**
         * whether to include reversible form of pseudonymized value in output
         */
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        @Builder.Default
        Boolean includeReversible = false;

        /**
         * values at this json path(es) matching regex will be pseudonymized
         *
         * if regex has a capture group, only portion of value captured by first group will be
         * pseudonymized and replaced in the content
         *
         * if regex does NOT match content, content matching to JSON path is REDACTED.
         *
         * Use case: format preserving pseudonymization, namely Microsoft Graph API encodes email
         * aliases as smtp:mailbox@domain.com or SMTP:mailbox@domain.com, depending on secondary
         * or primary; we want to pseudonymize only the actual mailbox portion
         *
         * @since 0.4.36
         */
        @NonNull
        String regex;

        @Override
        public PseudonymizeRegexMatches clone() {
            return this.toBuilder()
                .clearJsonPaths()
                .jsonPaths(new ArrayList<>(this.jsonPaths))
                .clearFields()
                .fields(new ArrayList<>(this.fields))
                .build();
        }

        @JsonIgnore
        @Override
        public Boolean getIncludeOriginal() {
            return false;
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
        @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @SuperBuilder(toBuilder = true)
    @NoArgsConstructor //for Jackson
    @Getter
    public static class TextDigest extends Transform {
        public TextDigest clone()  {
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
