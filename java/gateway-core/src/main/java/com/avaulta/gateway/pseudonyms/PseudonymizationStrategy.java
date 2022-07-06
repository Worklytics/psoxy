package com.avaulta.gateway.pseudonyms;

import java.util.function.Function;

/**
 *
 */
public interface PseudonymizationStrategy {

    /**
     * @param identifier       to pseudonymize
     * @param canonicalization
     * @return base64-url-encoded pseudonym (persistent/consistent pseudonym)
     */
    String getPseudonym(String identifier, Function<String, String> canonicalization);

    /**
     * @param identifier       to pseudonymize
     * @param canonicalization used to consistently pseudonymize identifiers that are 'canonically
     *                         equivalent'; not byte-wise equal, but are intended to reference
     *                         the same entity - differences are formatting
     * @return base64-url-encoding of pseudonym + key for reversing that pseudonym
     * NOTE: ability to reverse may depend on state of this implementation, eg, secrets that
     * it holds, passage of time, etc.
     */
    String getKeyedPseudonym(String identifier, Function<String, String> canonicalization);

    /**
     *
     * @param reversiblePseudonym base64-url-encoded ciphertext
     * @return plaintext that was originally passed to this EncryptionStrategy
     */
    String getIdentifier(String reversiblePseudonym);
}
