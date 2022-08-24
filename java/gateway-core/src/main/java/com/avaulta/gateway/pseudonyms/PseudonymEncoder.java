package com.avaulta.gateway.pseudonyms;

import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;

public interface PseudonymEncoder {

    enum Implementations {

        /**
         * encode Pseudonym as a JSON-object
         *
         * @see UrlSafeTokenPseudonymEncoder
         */
        JSON,

        /**
         * encode Pseudonym in a proprietary format that is expected to be safe for use in URLs
         *
         * @see UrlSafeTokenPseudonymEncoder
         */
        URL_SAFE_TOKEN,
    }

    String encode(Pseudonym pseudonym);

    Pseudonym decode(String pseudonym);

    /**
     * returns string after reversing all keyed pseudonyms created with this
     * PseudonymizationStrategy that it contains (if any)
     *
     * @param containsKeyedPseudonyms string that may contain keyed pseudonyms
     * @param reidentifier             function to reverse keyed pseudonym
     * @return string with all keyed pseudonyms it contains, if any, reversed to originals
     */
    String decodeAndReverseAllContainedKeyedPseudonyms(String containsKeyedPseudonyms, ReversiblePseudonymStrategy reidentifier);
}
