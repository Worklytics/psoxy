package co.worklytics.psoxy;

import com.google.api.client.http.GenericUrl;
import lombok.*;
import org.apache.commons.lang3.tuple.Pair;

import java.io.Serializable;
import java.util.List;

public interface Sanitizer {

    /**
     * options that control how data source API is sanitized, including support for:
     *   - pseudonymizing values in JSON response
     *   - redacting values in JSON response
     *
     * in principle, these are generic transforms and - with pseudonymization - it makes sense
     * to support composing them. But a generic system for rules + transform chain is more than
     * the use case requires - but probably more complicated to develop with. Goal should be
     * to make specifying an implementation of Sanitizer.Options to be as simple as possible.
     */
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

        /**
         * list of relativeUrl regex --> jsonPaths of values to pseudonymize
         *
         * q: do we need to support canonicalization first?
         *     eg, "Erik Schultink <erik@worklytics.co>" --> "erik@worklytics.co" -->
         *
         * q: domain (organization) preservation - how??
         */
        @Singular
        @Getter
        List<Pair<String, List<String>>> pseudonymizations;

        /**
         * list relativeUrl regex --> jsonPaths of values to redact
         *
         * q: is a values white list better? challenge is implementing that in a good way
         */
        @Singular
        @Getter
        List<Pair<String, List<String>>> redactions;

        //q: add regexes to whitelist endpoints that we actually use??
    }

    /**
     * sanitize jsonResponse received from url, according any options set on Sanitizer
     */
    String sanitize(GenericUrl url, String jsonResponse);
}
