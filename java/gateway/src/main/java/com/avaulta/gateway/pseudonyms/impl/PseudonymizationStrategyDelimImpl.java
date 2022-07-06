package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.PseudonymizationStrategy;

import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.Validate;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.function.Function;



@RequiredArgsConstructor
public class PseudonymizationStrategyDelimImpl implements PseudonymizationStrategy {

    static final String IV_DELIMITER = ":";
    static final int CIPHER_BLOCK_SIZE_BYTES = 16; //128-bits; AES uses 128-bit blocks, regardless of key-length

    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();

    @Getter
    final String salt;
    @Getter
    final SecretKeySpec encryptionKey;

    @SneakyThrows
    Cipher getCipherInstance() {
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
    }


    @Override
    public String getPseudonym(String identifier, Function<String, String> canonicalization) {
        return encoder.encodeToString(DigestUtils.sha256(canonicalization.apply(identifier) + getSalt()));
    }

    @SneakyThrows
    @Override
    public String getKeyedPseudonym(@NonNull String identifier, Function<String, String> canonicalization) {
        Cipher cipher = getCipherInstance();

        byte[] hash = decoder.decode(getPseudonym(identifier, canonicalization).getBytes());
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
        Validate.validIndex(parts, 1, "Invalid ciphertext: %s", reversiblePseudonym);
        byte[] iv = extractIv(decoder.decode(parts[0]));
        byte[] cryptoText = decoder.decode(parts[1]);

        Cipher cipher = getCipherInstance();
        cipher.init(Cipher.DECRYPT_MODE, getEncryptionKey(), new IvParameterSpec(iv));

        byte[] plain = cipher.doFinal(cryptoText);
        return new String(plain, StandardCharsets.UTF_8);
    }
}
