package co.worklytics.psoxy;

public interface EncryptionStrategy {

    /**
     *
     * @param plaintext
     * @return base64-url-encoded ciphertext
     */
    String encrypt(String plaintext);

    /**
     *
     * @param ciphertext
     * @return plaintext that was originally passed to this EncryptionStrategy

     */
    String decrypt(String ciphertext);
}
