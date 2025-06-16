package co.worklytics.psoxy.gateway.auth;

import lombok.NonNull;
import lombok.SneakyThrows;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;

public class Base64KeyClient implements PublicKeyStoreClient {

    @Override
    public PublicKeyStore getId() {
        return PublicKeyStore.BASE64;
    }

    @SneakyThrows
    @Override
    public Set<RSAPublicKey> getPublicKeys(@NonNull PublicKeyRef keyRef) {
        if (!keyRef.getStore().equals(PublicKeyStore.BASE64)) {
            throw new IllegalArgumentException("Key ref not compatible with Base64KeyClient: " + keyRef.encodeAsString());
        }

        byte[] decoded = Base64.getDecoder().decode(keyRef.getId());
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return Collections.singleton((RSAPublicKey) keyFactory.generatePublic(spec));
    }
}
