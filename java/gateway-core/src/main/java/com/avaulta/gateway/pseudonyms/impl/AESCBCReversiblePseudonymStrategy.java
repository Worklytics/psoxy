package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.DeterministicPseudonymStrategy;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.ReversiblePseudonymStrategy;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;


import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;


/**
 * would it be better to use AES-SIV (such as AES-GCM-SIV?)
 *  - GCP DLP uses it for similar tokenization case
 *  - but specifically for pseudonymization case, where we want expect deterministic pseudonyms
 *    over long time spans (indefinite), the concern about re-use of nonce isn't much of a concern
 */
@RequiredArgsConstructor
public class AESCBCReversiblePseudonymStrategy implements ReversiblePseudonymStrategy {


    @Getter
    final DeterministicPseudonymStrategy deterministicPseudonymStrategy;

    @Getter
    final SecretKeySpec encryptionKey;

    static final int CIPHER_BLOCK_SIZE_BYTES = 16; //128-bits; AES uses 128-bit blocks, regardless of key-length
    @SneakyThrows
    Cipher getCipherInstance() {
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
    }

    @SneakyThrows
    @Override
    public byte[] getReversiblePseudonym(@NonNull String identifier, Function<String, String> canonicalization) {
        Cipher cipher = getCipherInstance();

        byte[] hash = deterministicPseudonymStrategy.getPseudonym(identifier, canonicalization);

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
        byte[] ciphertext = cipher.doFinal(identifier.getBytes(StandardCharsets.UTF_8));

        return arrayConcat(hash, ciphertext);
    }

    //q: is there not a lib method for this??
    byte[] arrayConcat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    byte[] extractIv(byte[] reversiblePseudonym) {
        return Arrays.copyOfRange(reversiblePseudonym, 0, CIPHER_BLOCK_SIZE_BYTES);
    }

    @SneakyThrows
    @Override
    public String getIdentifier(@NonNull byte[] reversiblePseudonym) {

        byte[] iv = extractIv(reversiblePseudonym);
        byte[] cryptoText =
            Arrays.copyOfRange(reversiblePseudonym, deterministicPseudonymStrategy.getPseudonymLength(), reversiblePseudonym.length);

        Cipher cipher = getCipherInstance();
        cipher.init(Cipher.DECRYPT_MODE, getEncryptionKey(), new IvParameterSpec(iv));

        byte[] plain = cipher.doFinal(cryptoText);
        return new String(plain, StandardCharsets.UTF_8);
    }

}
