package com.avaulta.gateway.tokens.impl;

import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import lombok.*;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.function.Function;

@Builder
@RequiredArgsConstructor
public class AESReversibleTokenizationStrategy implements ReversibleTokenizationStrategy {


    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    public static CipherSuite GCM = GenericCipherSuite.builder()
        .cipher("AES/GCM/NoPadding")
        .parameterSpecGenerator( (byte[] deterministicPseudonym) ->
            new GCMParameterSpec(GCM_TAG_LENGTH*8, Arrays.copyOfRange(deterministicPseudonym, 0, GCM_IV_LENGTH)))
        .build();

    static final int CIPHER_BLOCK_SIZE_BYTES = 16; //128-bits; AES uses 128-bit blocks, regardless of key-length


    public static CipherSuite CBC = GenericCipherSuite.builder()
        .cipher("AES/CBC/PKCS5Padding")
        .parameterSpecGenerator((byte[] deterministicPseudonym) ->
            new IvParameterSpec(Arrays.copyOfRange(deterministicPseudonym, 0, CIPHER_BLOCK_SIZE_BYTES)))

        .build();

    interface CipherSuite {

        String getCipher();

        Function<byte[], AlgorithmParameterSpec> getParameterSpecGenerator();
    }

    @SneakyThrows
    public static SecretKeySpec aesKeyFromPassword(String password, String salt) {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 65536, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }


    @Builder
    @Value
    static class GenericCipherSuite implements CipherSuite {

        private String cipher;

        final Function<byte[], AlgorithmParameterSpec> parameterSpecGenerator;
    }

    final CipherSuite cipherSuite;

    @Getter
    final DeterministicTokenizationStrategy deterministicTokenizationStrategy;

    @Getter
    final SecretKeySpec key;

    @SneakyThrows
    Cipher getCipherInstance() {
        return Cipher.getInstance(cipherSuite.getCipher());
    }

    //64-bytes
    @SneakyThrows
    @Override
    public byte[] getReversibleToken(@NonNull String identifier, Function<String, String> canonicalization) {
        if (getKey() == null) {
            throw new IllegalStateException("No key set on AESReversibleTokenizationStrategy");
        }

        Cipher cipher = getCipherInstance();

        byte[] deterministicPseudonym = deterministicTokenizationStrategy.getToken(identifier, canonicalization);

        cipher.init(Cipher.ENCRYPT_MODE, getKey(), cipherSuite.getParameterSpecGenerator().apply(deterministicPseudonym));
        byte[] ciphertext = cipher.doFinal(identifier.getBytes(StandardCharsets.UTF_8));

        return arrayConcat(deterministicPseudonym, ciphertext);
    }

    //q: is there not a lib method for this??
    byte[] arrayConcat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    @SneakyThrows
    @Override
    public String getOriginalDatum(@NonNull byte[] reversibleToken) {
        if (getKey() == null) {
            throw new IllegalStateException("No key set on AESReversibleTokenizationStrategy");
        }

        byte[] cryptoText = Arrays.copyOfRange(reversibleToken, deterministicTokenizationStrategy.getTokenLength(), reversibleToken.length);

        Cipher cipher = getCipherInstance();

        cipher.init(Cipher.DECRYPT_MODE, getKey(), cipherSuite.getParameterSpecGenerator().apply(reversibleToken));

        byte[] plain = cipher.doFinal(cryptoText);
        return new String(plain, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "AESReversiblePseudonymStrategy(" + cipherSuite.getCipher() + ")";
    }


}
