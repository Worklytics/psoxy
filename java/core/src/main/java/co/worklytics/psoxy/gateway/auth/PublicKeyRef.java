package co.worklytics.psoxy.gateway.auth;

/**
 * reference to a public key
 *
 * @param store the public key store where this key is stored
 *             (e.g. GCP KMS, AWS KMS, Base64 encoded key, etc.)
 * @param id the identifier of the public key within that store; unique given the store
 */
public record PublicKeyRef(PublicKeyStore store, String id) {

    public String encodeAsString() {
        return store.getIdentifier() + ":" + id;
    }

    public static PublicKeyRef fromString(String keyRef) {
        String[] parts = keyRef.split(":", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid key reference format: " + keyRef);
        }

        PublicKeyStore store = PublicKeyStore.fromIdentifier(parts[0]);
        String id = parts[1];

        return new PublicKeyRef(store, id);
    }

}
