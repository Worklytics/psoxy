package co.worklytics.psoxy.rules;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.*;
import lombok.extern.java.Log;

import java.io.Serializable;
import java.util.List;

/**
 * rules that control how data source API is sanitized, including support for:
 * - pseudonymizing values in JSON response
 * - redacting values in JSON response
 * <p>
 * in principle, these are generic transforms and - with pseudonymization - it makes sense
 * to support composing them. But a generic system for rules + transform chain is more than
 * the use case requires - but probably more complicated to develop with. Goal should be
 * to make specifying an implementation of Sanitizer.Options to be as simple as possible.
 * <p>
 * q: good design?
 * - we now have 3 cases of value `Map`/`Transformation`
 */
@Builder(toBuilder = true)
@Log
@AllArgsConstructor //for builder
@NoArgsConstructor //for Jackson
@Getter
@EqualsAndHashCode
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(JsonInclude.Include.NON_NULL) //NOTE: despite name, also affects YAML encoding
public class Rules1 implements RuleSet, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * scopeId to set for any identifiers parsed from source that aren't email addresses
     *
     * NOTE: can be overridden by config, in case you're connecting to an on-prem / private instance
     * of the source and you don't want it's identifiers to be treated as under the default scope
     */
    @Getter
    String defaultScopeIdForSource;

    /**
     * if set to non-empty list, proxy will block calls to any endpoints with relative urls that do
     * not match at least one of the regexes in this list (if unset, calls to all endpoints will be
     * allowed via proxy)
     *
     * in effect, this allows for more granular restrictions than the source API implements
     *
     * NOTE: while it's hard to write list of regexes that match ONLY relativeUrls intended to be
     * allowed, for most REST APIs it's easy to write a list that excludes urls that are actual
     * endpoints you intend to block (as opposed to fake urls crafted to defeat the regex)
     *
     * common pitfalls:
     *   - not beginning these with `^`
     *   - using .* wildcard for path fragments rather than [^/]?* (all chars but /, greedy)
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Singular
    @Getter
    List<String> allowedEndpointRegexes;

    /**
     * values in response matching any of these rules will be pseudonymized
     */
    @Singular
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Rule> pseudonymizations;


    /**
     * values in response matching any of these rules will be pseudonymized but original value will
     * also be sent
     *
     * use case: fields that aren't PII, but need to be matched against pseudonymized data (eg, for
     * mailing list email addresses)
     */
    @Singular
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Rule> pseudonymizationWithOriginals;

    /**
     * values in response matching these any of these rules will be split based on conventions
     * for Email Headers, then pseudonymized
     */
    @Singular
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Rule> emailHeaderPseudonymizations;

    /**
     * list relativeUrl regex --> jsonPaths of values to redact
     * <p>
     * q: is a values white list better? challenge is implementing that in a good way
     */
    @Singular
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    List<Rule> redactions;

    /**
     * a rule for matching parts of JSON API responses.
     * <p>
     * match means the request URL matches `relativeUrlRegex` and the node in JSON response
     * matches at least one of `jsonPaths`
     */
    @Builder
    @AllArgsConstructor //for builder
    @NoArgsConstructor //for Jackson
    @Getter
    @JsonPropertyOrder(alphabetic = true)
    @EqualsAndHashCode
    public static class Rule implements Serializable {

        private static final long serialVersionUID = 1L;

        //must match request URL
        String relativeUrlRegex;

        // json nodes that match ANY of these paths will be matches
        @Singular
        List<String> jsonPaths;

        // Only for being used when the source is a CSV files;
        // the name of the columns that appear in the file as part of the header
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<String> csvColumns;
    }

    /**
     * build new Rules from this + another existing set
     * @param other
     * @return combined rule sets
     */
    public Rules1 compose(Rules1 other) {
        Rules1Builder builder = this.toBuilder();
        other.allowedEndpointRegexes.stream()
            .forEach(builder::allowedEndpointRegex);
        other.emailHeaderPseudonymizations.stream()
            .forEach(builder::emailHeaderPseudonymization);
        other.pseudonymizations.stream()
            .forEach(builder::pseudonymization);
        other.pseudonymizationWithOriginals.stream()
            .forEach(builder::pseudonymizationWithOriginal);
        other.redactions.stream()
            .forEach(builder::redaction);
        return builder.build();
    }
}
