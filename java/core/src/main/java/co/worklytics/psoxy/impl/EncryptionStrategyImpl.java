package co.worklytics.psoxy.impl;

import co.worklytics.psoxy.EncryptionStrategy;
import co.worklytics.psoxy.HashUtils;
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

@NoArgsConstructor(onConstructor_ = @Inject)
public class EncryptionStrategyImpl implements EncryptionStrategy {

    @Inject
    ConfigService config;
    SecretKeySpec encryptionKey;

    static final int CIPHER_BLOCK_SIZE_BYTES = 16; //128-bits; AES uses 128-bit blocks, regardless of key-length
    static final int ONE_WAY_PSEUDONYM_SIZE_BYTES = 32; //SHA-256

    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();



    @SneakyThrows
    Cipher getCipherInstance() {
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
    }


    @SneakyThrows
    @Override
    public String encrypt(@NonNull String plaintext) {
        Cipher cipher = getCipherInstance();

        //pass in a canonicalization function? if not, this won't match for the canonically-equivalent
        // identifier in different formats (eg, cased/etc)

        // this is really the 'pseudonym' for the plaintext
        byte[] hash = DigestUtils.sha256(plaintext + getSalt());


        //fundamental insight here: conventional encryption would generate a random IV; but this
        // would mean that repeated encryption of the same plaintext would yield different results.
        // that's GENERALLY a good thing - as it hides repeated values within the corpus being
        // encrypted - but in this use case, that's not a problem. Our goal isn't to obscure that
        // the author of an email is the same as a recipient of another email - but merely identify
        // that individual using some identifier that cannot be used to identify the natural person
        // to which it refers absent some other data that we can reasonably expect to be secret
        // (eg, a lookup table mapping the identifiers to PII; or secret used to encrypt the PII).
        IvParameterSpec ivParameter = new IvParameterSpec(extractIv(hash));


        cipher.init(Cipher.ENCRYPT_MODE, getEncryptionKey(), ivParameter);
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        return encoder.encodeToString(arrayConcat(hash, ciphertext));
    }

    //q: is there not a lib method for this??
    byte[] arrayConcat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    byte[] extractIv(byte[] hash) {
        return Arrays.copyOfRange(hash, 0, CIPHER_BLOCK_SIZE_BYTES);
    }

    @SneakyThrows
    @Override
    public String decrypt(@NonNull String encodedCiphertext) {

        byte[] decoded = decoder.decode(encodedCiphertext);
        byte[] iv = extractIv(decoded);
        byte[] cryptoText = Arrays.copyOfRange(decoded, ONE_WAY_PSEUDONYM_SIZE_BYTES, decoded.length);

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
