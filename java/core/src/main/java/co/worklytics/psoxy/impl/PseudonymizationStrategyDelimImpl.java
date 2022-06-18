package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.PseudonymizationStrategy;
import co.worklytics.psoxy.gateway.ConfigService;
import co.worklytics.psoxy.gateway.ProxyConfigProperty;
import com.google.common.base.Preconditions;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

/**
 * impl of EncryptionStrategy, using delimiters to split hash + cryptotext
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class PseudonymizationStrategyDelimImpl implements PseudonymizationStrategy {

    @Inject
    ConfigService config;
    SecretKeySpec encryptionKey;

    static final String IV_DELIMITER = ":";
    static final int CIPHER_BLOCK_SIZE_BYTES = 16; //128-bits; AES uses 128-bit blocks, regardless of key-length

    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();



    @SneakyThrows
    Cipher getCipherInstance() {
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
    }


    @Override
    public String getPseudonym(String identifier) {
        return encoder.encodeToString(DigestUtils.sha256(identifier + getSalt()));
    }

    @SneakyThrows
    @Override
    public String getPseudonymWithKey(@NonNull String identifier) {
        Cipher cipher = getCipherInstance();

        byte[] hash = decoder.decode(getPseudonym(identifier).getBytes());
        cipher.init(Cipher.ENCRYPT_MODE, getEncryptionKey(),
            new IvParameterSpec(extractIv(hash)));
        byte[] ciphertext = cipher.doFinal(identifier.getBytes(StandardCharsets.UTF_8));
        return encoder.encodeToString(hash) + IV_DELIMITER
            + encoder.encodeToString(ciphertext);
    }

    byte[] extractIv(byte[] hash) {
        return Arrays.copyOfRange(hash, 0, CIPHER_BLOCK_SIZE_BYTES);
    }

    @SneakyThrows
    @Override
    public String getIdentifier(@NonNull String reversiblePseudonym) {
        String[] parts = reversiblePseudonym.split(IV_DELIMITER);
        Preconditions.checkArgument(parts.length == 2, "Invalid ciphertext: %s", reversiblePseudonym);
        byte[] iv = extractIv(decoder.decode(parts[0]));
        byte[] cryptoText = decoder.decode(parts[1]);

        Cipher cipher = getCipherInstance();
        cipher.init(Cipher.DECRYPT_MODE, getEncryptionKey(), new IvParameterSpec(iv));

        byte[] plain = cipher.doFinal(cryptoText);
        return new String(plain, StandardCharsets.UTF_8);
    }

    String getSalt() {
        return config.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_SALT);
    }


    SecretKeySpec getEncryptionKey() {
        if (encryptionKey == null) {
            //q: validate key length? we expect 256-bit (32 bytes)
            String keyFromConfig = config.getConfigPropertyOrError(ProxyConfigProperty.PSOXY_ENCRYPTION_KEY);
            encryptionKey = new SecretKeySpec(Base64.getDecoder().decode(keyFromConfig), "AES");
        }
        return encryptionKey;
    }
}
