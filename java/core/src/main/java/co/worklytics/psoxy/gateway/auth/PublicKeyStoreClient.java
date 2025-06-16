package co.worklytics.psoxy.gateway.auth;

import java.security.interfaces.RSAPublicKey;
import java.util.Set;

/**
 * Client interface for accessing public keys from a key store.
 * This interface abstracts the retrieval of public keys based on their identifier.
 *
 * q: right name for this? what about the base64 implementation?
 */
public interface PublicKeyStoreClient {

    PublicKeyStore getId();

    /**
     * Returns all enabled public keys for the given key reference (to support key rotation).
     */
    Set<RSAPublicKey> getPublicKeys(PublicKeyRef keyRef);
}
