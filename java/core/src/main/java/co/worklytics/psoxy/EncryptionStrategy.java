package co.worklytics.psoxy;

public interface EncryptionStrategy {

    /**
     *
     * @param plaintext to encrypt
     * @return base64-url-encoded sha256 hash + ciphertext without padding.
     */
    String encrypt(String plaintext);

    /**
     *
     * @param encodedCiphertext base64-url-encoded ciphertext
     * @return plaintext that was originally passed to this EncryptionStrategy
     */
    String decrypt(String encodedCiphertext);
}
