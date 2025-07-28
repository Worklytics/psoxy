package co.worklytics.psoxy.gcp;

import co.worklytics.psoxy.gateway.auth.PublicKeyRef;
import co.worklytics.psoxy.gateway.auth.PublicKeyStore;
import co.worklytics.psoxy.gateway.auth.PublicKeyStoreClient;
import com.google.cloud.kms.v1.CryptoKeyVersion;
import com.google.cloud.kms.v1.CryptoKeyVersionName;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.PublicKey;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Streams;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.java.Log;

import javax.inject.Inject;
import javax.inject.Provider;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import java.security.interfaces.RSAPublicKey;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Implementation of PublicKeyStoreClient for GCP KMS.
 * This client retrieves public keys from Google Cloud Key Management Service (KMS).
 */
@Log
public class GcpKmsPublicKeyStoreClient implements PublicKeyStoreClient {

    private final Provider<KeyManagementServiceClient> clientProvider;

    @Inject
    public GcpKmsPublicKeyStoreClient( Provider<KeyManagementServiceClient> kmsClientProvider ) {
        this.clientProvider = kmsClientProvider;
    }

    @Override
    public PublicKeyStore getId() {
        return PublicKeyStore.GCP_KMS;
    }

    // Cache for individual public keys
    LoadingCache<PublicKeyRef, Set<RSAPublicKey>> publicKeysCache =
        CacheBuilder.newBuilder()
            .maximumSize(5)  // TBH, in gcp case not mor than
            .expireAfterAccess(Duration.ofMinutes(10))
            .build(new CacheLoader<>() {
                @NonNull
                @Override
                public Set<RSAPublicKey> load(@NonNull PublicKeyRef keyRef) {
                    return getPublicKeysWrapper(keyRef);
                }
            });

    @SneakyThrows
    @Override
    public Set<RSAPublicKey> getPublicKeys(@NonNull PublicKeyRef keyRef) {
        if (keyRef.id() == null) {
            throw new IllegalArgumentException("PublicKeyRef and its ID must not be null");
        }
        if (keyRef.store() != this.getId()) {
            throw new IllegalArgumentException("PublicKeyRef must be for GCP KMS, but was: " + keyRef.store());
        }
        return publicKeysCache.get(keyRef);
    }

     public Set<RSAPublicKey> getPublicKeysWrapper(@NonNull PublicKeyRef keyRef) {
        try (KeyManagementServiceClient client = clientProvider.get()) {
            // keyRef.getId() expected to be full resource name: projects/.../locations/.../keyRings/.../cryptoKeys/.../cryptoKeyVersions/...
            String resourceName = keyRef.id();
            KeyManagementServiceClient.ListCryptoKeyVersionsPagedResponse versions = client.listCryptoKeyVersions( keyRef.id());

            List<String> activeVersions = StreamSupport.stream(versions.iterateAll().spliterator(), true)
                .filter(version -> version.getState() == CryptoKeyVersion.CryptoKeyVersionState.ENABLED)
                .map(CryptoKeyVersion::getName)
                .collect(Collectors.toList());

            Set<RSAPublicKey> keys = new HashSet<>();
            activeVersions = activeVersions.stream()
                .filter(version -> version.startsWith(resourceName))
                .collect(Collectors.toList());
            for (String  version : activeVersions) {
                try {
                    // Attempt to get the public key for the version
                    PublicKey pub  = client.getPublicKey(CryptoKeyVersionName.parse(version));

                    String pem = pub.getPem();
                    String base64 = pem.replaceAll("-----BEGIN PUBLIC KEY-----", "")
                        .replaceAll("-----END PUBLIC KEY-----", "")
                        .replaceAll("\\s", "");
                    byte[] der = Base64.getDecoder().decode(base64);
                    X509EncodedKeySpec keySpec = new X509EncodedKeySpec(der);
                    KeyFactory kf = KeyFactory.getInstance("RSA");
                    RSAPublicKey rsaKey = (RSAPublicKey) kf.generatePublic(keySpec);
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to retrieve or parse public key for version: " + version, e);
                }
            }
            return keys;
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch public key from GCP KMS", e);
        }
    }
}
