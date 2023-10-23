package com.avaulta.gateway.pseudonyms;

import com.avaulta.gateway.pseudonyms.impl.UrlSafeTokenPseudonymEncoder;
import com.avaulta.gateway.pseudonyms.impl.Base64UrlSha256HashPseudonymEncoder;

public interface PseudonymEncoder {

    enum Implementations {

        /**
         * encode Pseudonym as a JSON-object
         *
         */
        JSON,

        /**
         * encode Pseudonym in a proprietary format that is expected to be safe for use in URLs
         *
         * @see UrlSafeTokenPseudonymEncoder
         */
        URL_SAFE_TOKEN,

        /**
         * encode Pseudonym's hash as base64-URL, without padding.
         *
         * (this is equivalent to the URL_SAFE_TOKEN format, but without prefix (`t~`/`p~`); and
         * without any additional data, such as encrypted form of pseudonym OR any form of domain)
         *
         * @see Base64UrlSha256HashPseudonymEncoder
         */
        URL_SAFE_HASH_ONLY,
    }

    String encode(Pseudonym pseudonym);

    Pseudonym decode(String pseudonym);

    /**
     * @param possiblePseudonym
     * @return whether possiblePseudonym can be decoded with this PseudonymEncoder
     */
    boolean canBeDecoded(String possiblePseudonym);

}
