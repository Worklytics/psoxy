package co.worklytics.psoxy.gateway.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * enum of key providers, used to identify the kind of key provider
 *
 */
@RequiredArgsConstructor
public enum PublicKeyStore {

    AWS_KMS("aws-kms"),
    BASE64("base64"), // actually an inline base64-encoded public key
    GCP_KMS("gcp-kms"),
    ;

    @Getter
    private final String identifier;

    static PublicKeyStore fromIdentifier(String identifier) {
        for (PublicKeyStore provider : PublicKeyStore.values()) {
            if (provider.identifier.equals(identifier)) {
                return provider;
            }
        }
        throw new IllegalArgumentException("Unknown key store identifier: " + identifier);
    }
}
