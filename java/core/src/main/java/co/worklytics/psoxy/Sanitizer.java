package co.worklytics.psoxy;

import lombok.*;

import java.io.Serializable;
import java.net.URL;

public interface Sanitizer {

    @With
    @Builder
    @Value
    class Options implements Serializable {

        private static final long serialVersionUID = 2L;

        /**
         * salt used to generate pseudonyms
         *
         * q: split this out? it's per-customer, not per-source API (although it *could* be the
         * later, if you don't need to match with it)
         */
        String pseudonymizationSalt;


        /**
         * scope to use where logic + rules don't imply a match
         */
        String defaultScopeId;

        //q: add regexes to whitelist endpoints that we actually use??
        Rules rules;


    }

    /**
     * @param url to test
     * @return whether url is even allowed to be called via proxy, given Sanitizer rule set
     *
     * q: some scope question about whether this is beyond Sanitizer responsibility or not
     *   args for:
     *      - it's an interpretation of rules
     *      - correct if 'santizer' is sanitizing an "API" rather than "API response content"
     *      - sanitization can't really be decoupled from the semantics of the endpoints in question
     *   args against:
     *      - could split this into two classes, with one that deals with endpoint level stuff, such
     *        as 1) what endpoints allowed, and 2) what pseudonymizations and redactions to apply
     *        to response given the endpoint; and the other(s) which just do the
     *        pseudonymization/redaction (eg, pseudonymize(jsonPAths, content); redact(jsonPaths, content))
     *        - just invariably that's quite coupled, per above
     */
    boolean isAllowed(URL url);

    /**
     * sanitize jsonResponse received from url, according any options set on Sanitizer
     */
    String sanitize(URL url, String jsonResponse);

    /**
     * @param identifier to pseudonymize
     * @return identifier as a PseudonymizedIdentity
     */
    PseudonymizedIdentity pseudonymize(String identifier);

    /**
     * @param identifier to pseudonymize
     * @return identifier as a PseudonymizedIdentity
     */
    PseudonymizedIdentity pseudonymize(Number identifier);

}
