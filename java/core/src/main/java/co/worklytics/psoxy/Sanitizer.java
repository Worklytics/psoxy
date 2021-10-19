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
