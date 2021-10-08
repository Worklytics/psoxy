package co.worklytics.psoxy;

import com.google.api.client.http.GenericUrl;
import lombok.*;

import java.io.Serializable;
import java.util.List;

public interface Sanitizer {


    /**
     * rules that control how data source API is sanitized, including support for:
     *   - pseudonymizing values in JSON response
     *   - redacting values in JSON response
     *
     * in principle, these are generic transforms and - with pseudonymization - it makes sense
     * to support composing them. But a generic system for rules + transform chain is more than
     * the use case requires - but probably more complicated to develop with. Goal should be
     * to make specifying an implementation of Sanitizer.Options to be as simple as possible.
     *
     * q: good design?
     *   - we now have 3 cases of value `Map`/`Transformation`
     *
     */
    @Builder
    @Value
    class Rules {

        /**
         * a rule for matching parts of JSON API responses.
         *
         * match means the request URL matches `relativeUrlRegex` and the node in JSON response
         * matches at least one of `jsonPaths`
         */
        @Builder
        @Value
        public static class Rule {

            //must match request URL
            String relativeUrlRegex;

            // json nodes that match ANY of these paths will be matches
            @Singular
            List<String> jsonPaths;
        }

        /**
         * values in response matching any of these rules will be pseudonymized
         *
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
         *
         * q: is a values white list better? challenge is implementing that in a good way
         */
        @Singular
        @Getter
        List<Rule> redactions;
    }



    @With
    @Builder
    @Value
    class Options implements Serializable {

        /**
         * salt used to generate pseudonyms
         *
         * q: split this out? it's per-customer, not per-source API (although it *could* be the
         * later, if you don't need to match with it)
         */
        String pseudonymizationSalt;


        //q: add regexes to whitelist endpoints that we actually use??
        Rules rules;
    }

    /**
     * sanitize jsonResponse received from url, according any options set on Sanitizer
     */
    String sanitize(GenericUrl url, String jsonResponse);
}
