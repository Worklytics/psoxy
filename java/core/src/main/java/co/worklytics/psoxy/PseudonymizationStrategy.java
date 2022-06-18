package co.worklytics.psoxy;

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
     * @param canonicalization
     * @return base64-url-encoding of pseudonym + key for reversing that pseudonym
     * NOTE: ability to reverse may depend on state of this implementation, eg, secrets that
     * it holds, passage of time, etc.
     * eg, may not reverse beyond a window because of key rotation/etc.
     */
    String getPseudonymWithKey(String identifier, Function<String, String> canonicalization);

    /**
     *
     * @param reversiblePseudonym base64-url-encoded ciphertext
     * @return plaintext that was originally passed to this EncryptionStrategy
     */
    String getIdentifier(String reversiblePseudonym);
}
