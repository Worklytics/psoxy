package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.auth.PublicKeyRef;
import co.worklytics.psoxy.gateway.auth.PublicKeyStore;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;

import javax.inject.Inject;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;

/**
 * AWS KMS implementation of PublicKeyStoreClient using AWS SDK v2
 */
public class AwsKmsPublicKeyStoreClient implements PublicKeyStoreClient {

    private final KmsClient kmsClient;

    @Inject
    public AwsKmsPublicKeyStoreClient(KmsClient kmsClient) {
        this.kmsClient = kmsClient;
    }

    @Override
    public PublicKeyStore getId() {
        return PublicKeyStore.AWS_KMS;
    }

    @Override
    public RSAPublicKey getPublicKey(PublicKeyRef keyRef) {
        if (keyRef.getStore() != PublicKeyStore.AWS_KMS) {
            throw new IllegalArgumentException("KeyRef store must be AWS_KMS");
        }
        String keyId = keyRef.getId();
        GetPublicKeyRequest request = GetPublicKeyRequest.builder().keyId(keyId).build();
        GetPublicKeyResponse response = kmsClient.getPublicKey(request);
        byte[] derEncoded = response.publicKey().asByteArray();
        try {
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(derEncoded);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            return (RSAPublicKey) publicKey;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse public key from KMS", e);
        }
    }
}

