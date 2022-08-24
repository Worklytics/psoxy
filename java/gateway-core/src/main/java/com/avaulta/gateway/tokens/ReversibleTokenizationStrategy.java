package com.avaulta.gateway.tokens;

import java.util.function.Function;

/**
 *
 */
public interface ReversibleTokenizationStrategy {


    /**
     * @param originalDatum    to tokenize
     * @param canonicalization used to consistently tokenize datums that are 'canonically
     *                         equivalent'; not byte-wise equal, but are intended to reference
     *                         the same thing - differences are formatting
     * @return hash of canonicalized dataum + encrypted form of originalDatum, that can potentially
     * be reversed back to originalDatum
     * NOTE: ability to reverse may depend on state of this implementation, eg, secrets that
     * it holds, passage of time, etc.
     */
    byte[] getReversibleToken(String originalDatum, Function<String, String> canonicalization);

    /**
     * @param originalDatum    to tokenize
     * @return hash of dataum + encrypted form of originalDatum, that can potentially
     * be reversed back to originalDatum
     * NOTE: ability to reverse may depend on state of this implementation, eg, secrets that
     * it holds, passage of time, etc.
     */
    default byte[] getReversibleToken(String originalDatum) {
        return getReversibleToken(originalDatum, Function.identity());
    }

    /**
     *
     * @param reversibleToken ciphertext, if it was created with this TokenizationStrategy
     * @return plaintext that was originally passed to this TokenizationStrategy
     */
    String getOriginalDatum(byte[] reversibleToken);


}
