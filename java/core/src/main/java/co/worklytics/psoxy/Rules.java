package co.worklytics.psoxy;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.Value;
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
@Builder
@Value
@Log
public class Rules implements Serializable {

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
     * a rule for matching parts of JSON API responses.
     * <p>
     * match means the request URL matches `relativeUrlRegex` and the node in JSON response
     * matches at least one of `jsonPaths`
     */
    @Builder
    @Value
    public static class Rule implements Serializable {

        private static final long serialVersionUID = 1L;

        //must match request URL
        String relativeUrlRegex;

        // json nodes that match ANY of these paths will be matches
        @Singular
        List<String> jsonPaths;
    }

    /**
     * values in response matching any of these rules will be pseudonymized
     */
    @Singular
    @Getter
    List<Rule> pseudonymizations;

    /**
     * values in response matching these any of these rules will be split based on conventions
     * for Email Headers, then pseudonymized
     */
    @Singular
    @Getter
    List<Rule> emailHeaderPseudonymizations;

    /**
     * list relativeUrl regex --> jsonPaths of values to redact
     * <p>
     * q: is a values white list better? challenge is implementing that in a good way
     */
    @Singular
    @Getter
    List<Rule> redactions;

}
