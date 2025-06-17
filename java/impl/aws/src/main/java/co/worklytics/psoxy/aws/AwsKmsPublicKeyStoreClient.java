package co.worklytics.psoxy.aws;

import co.worklytics.psoxy.gateway.auth.PublicKeyRef;
import co.worklytics.psoxy.gateway.auth.PublicKeyStore;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import com.amazonaws.services.kms.model.KMSInvalidStateException;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.NonNull;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GetPublicKeyRequest;
import software.amazon.awssdk.services.kms.model.GetPublicKeyResponse;

import javax.inject.Inject;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * AWS KMS implementation of PublicKeyStoreClient using AWS SDK v2
 *
 * NOTE: key rotation - aws does not support native "key rotation" in the same way as GCP; there are not multiple
 * valid versions of a key or alias; rather, you have an alias point to a given key, and rotate by creating a new key
 * and pointing it to that. so for webhook collector purposes we will have to configure TWO keys for aws:kms, a
 * "current" and a "previous" alias, and attempt verification with both, if they're valid.
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

    // Cache for individual public keys
    LoadingCache<PublicKeyRef, Set<RSAPublicKey>> publicKeysCache =
        CacheBuilder.newBuilder()
            .maximumSize(5)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(new CacheLoader<>() {
                @NonNull
                @Override
                public Set<RSAPublicKey> load(@NonNull PublicKeyRef keyRef) {
                    return getPublicKeysWrapper(keyRef);
                }
            });

    /**
     * possibly empty, if key no longer valid/enabled.
     *
     * @param keyRef
     * @return
     */
    @Override
    public Set<RSAPublicKey> getPublicKeys(PublicKeyRef keyRef) {
        if (keyRef.store() != PublicKeyStore.AWS_KMS) {
            throw new IllegalArgumentException("KeyRef store must be AWS_KMS");
        }
        try {
            return publicKeysCache.get(keyRef);
        } catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
        }
    }

    // wraps get with something that returns a Set, considering possible 'null' return value from getPublicKey,
    // if it has been disabled or deleted in AWS KMS (expected due to rotation)
    Set<RSAPublicKey> getPublicKeysWrapper(PublicKeyRef keyRef) {
        RSAPublicKey key = this.getPublicKey(keyRef);
        if (key  == null) {
            return Collections.emptySet();
        } else {
            return new HashSet<>(Collections.singletonList(key));
        }
    }

    RSAPublicKey getPublicKey(PublicKeyRef keyRef) {
        if (keyRef.store() != PublicKeyStore.AWS_KMS) {
            throw new IllegalArgumentException("KeyRef store must be AWS_KMS");
        }
        String keyId = keyRef.id();
        GetPublicKeyRequest request = GetPublicKeyRequest.builder().keyId(keyId).build();
        try {
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
        } catch (KMSInvalidStateException e) {
            return null;
        }
    }
}
