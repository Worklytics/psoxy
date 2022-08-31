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
     * @param possiblePseudonym
     * @return whether possiblePseudonym can be decoded with this PseudonymEncoder
     */
    boolean canBeDecoded(String possiblePseudonym);

}
