package co.worklytics.psoxy.gateway.auth;

import java.security.interfaces.RSAPublicKey;

/**
 * Client interface for accessing public keys from a key store.
 * This interface abstracts the retrieval of public keys based on their identifier.
 *
 * q: right name for this? what about the base64 implementation?
 */
public interface PublicKeyStoreClient {

    PublicKeyStore getId();

    RSAPublicKey getPublicKey(PublicKeyRef keyRef);
}
