package co.worklytics.psoxy.gateway.auth;

import lombok.Value;

/**
 * reference to a k
 */
@Value
public class PublicKeyRef {

    PublicKeyStore store;

    /**
     * identifies the key, given its store
     */
    String id;

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
