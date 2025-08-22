package co.worklytics.psoxy.gateway.auth;

import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import lombok.Value;

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
    Map<PublicKeyVersionId, RSAPublicKey> getPublicKeys(PublicKeyRef keyRef);


    @Value
    public static class PublicKeyVersionId {

        final PublicKeyStore store;
        final String keyId;
        final String versionId;

        public String toString() {
            return Arrays.asList(store.getIdentifier(), keyId, versionId).stream()
            .map(StringUtils::trimToNull)
            .filter(s -> s != null)
            .collect(Collectors.joining(":"));
        }
    }
}
