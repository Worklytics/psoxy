package co.worklytics.psoxy;

/**
 *
 */
public interface PseudonymizationStrategy {

    /**
     *
     * @param identifier to pseudonymize
     * @return base64-url-encoded pseudonym
     */
    String getPseudonym(String identifier);

    /**
     *
     * @param identifier to pseudonymize
     * @return base64-url-encoding of pseudonym + key for reversing that pseudonym
     *         NOTE: ability to reverse may depend on state of this implementation, eg, secrets that
     *         it holds, time, and

     */
    String getPseudonymWithKey(String identifier);

    /**
     *
     * @param reversiblePseudonym base64-url-encoded ciphertext
     * @return plaintext that was originally passed to this EncryptionStrategy
     */
    String getIdentifier(String reversiblePseudonym);
}
