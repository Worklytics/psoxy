package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.EncryptionStrategy;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.google.common.base.Preconditions;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@NoArgsConstructor(onConstructor_ = @Inject)
public class EncryptionStrategyImpl implements EncryptionStrategy {

    @Inject
    ConfigService config;

    SecretKeySpec encryptionKey;

    static final String IV_DELIMITER = ":";


    @SneakyThrows
    @Override
    public String encrypt(String plaintext) {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, getEncryptionKey());
        IvParameterSpec ivParameterSpec = cipher.getParameters().getParameterSpec(IvParameterSpec.class);
        byte[] cryptoText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] iv = ivParameterSpec.getIV();
        return Base64.getUrlEncoder().encodeToString(iv) + IV_DELIMITER + Base64.getUrlEncoder().encodeToString(cryptoText);
    }

    @SneakyThrows
    @Override
    public String decrypt(String ciphertext) {
        String[] parts = ciphertext.split(IV_DELIMITER);
        Preconditions.checkArgument(parts.length == 2, "Invalid ciphertext: %s", ciphertext);
        byte[] iv = Base64.getUrlDecoder().decode(parts[0]);
        byte[] cryptoText = Base64.getUrlDecoder().decode(parts[1]);

        Cipher pbeCipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        pbeCipher.init(Cipher.DECRYPT_MODE, getEncryptionKey(), new IvParameterSpec(iv));

        byte[] plain = pbeCipher.doFinal(cryptoText);
        return new String(plain, StandardCharsets.UTF_8);
    }


    SecretKeySpec getEncryptionKey() {
        if (encryptionKey == null) {
            String keyFromConfig = config.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_ENCRYPTION_KEY);
            encryptionKey = new SecretKeySpec(Base64.getDecoder().decode(keyFromConfig), "AES");
        }
        return encryptionKey;
    }
}
