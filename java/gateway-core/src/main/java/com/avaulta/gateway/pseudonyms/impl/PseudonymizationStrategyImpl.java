package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.PseudonymizationStrategy;
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
import java.util.Base64;
import java.util.function.Function;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class PseudonymizationStrategyImpl implements PseudonymizationStrategy {

    @Getter
    final String salt;
    @Getter
    final SecretKeySpec encryptionKey;

    /**
     * URL-safe prefix to put in front of reversible pseudonyms
     *
     * q: make configurable, to support compatibility with various REST-API clients??
     *
     * alternatives:
     *   - prefix + suffix to make stronger
     *
     */
    static final String PREFIX = "p~";

    static final int CIPHER_BLOCK_SIZE_BYTES = 16; //128-bits; AES uses 128-bit blocks, regardless of key-length
    static final int PSEUDONYM_SIZE_BYTES = 32; //SHA-256

    //length of base64-url-encoded IV + ciphertext
    static final int KEYED_PSEUDONYM_LENGTH_WITHOUT_PREFIX = 43;

    static final Pattern KEYED_PSEUDONYM_PATTERN =
        //Pattern.compile("p\\~[a-zA-Z0-9_-]{43}"); //not clear to me why this doesn't work
        Pattern.compile("p\\~[a-zA-Z0-9_-]{" + KEYED_PSEUDONYM_LENGTH_WITHOUT_PREFIX + ",}");

    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();

    @SneakyThrows
    Cipher getCipherInstance() {
        return Cipher.getInstance("AES/CBC/PKCS5Padding");
    }


    //32-bytes
    @Override
    public String getPseudonym(String identifier, Function<String, String> canonicalization) {
        //pass in a canonicalization function? if not, this won't match for the canonically-equivalent
        // identifier in different formats (eg, cased/etc)

        // if pseudonyms too long, could cut this to MD5 (save 16 bytes) or SHA1 (save 12 bytes)
        // for our implementation, that should still be good enough
        return encoder.encodeToString(DigestUtils.sha256(canonicalization.apply(identifier) + getSalt()));
    }

    //64-bytes
    @SneakyThrows
    @Override
    public String getKeyedPseudonym(@NonNull String identifier, Function<String, String> canonicalization) {
        Cipher cipher = getCipherInstance();

        byte[] hash = decoder.decode(getPseudonym(identifier, canonicalization).getBytes());

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

        return PREFIX + encoder.encodeToString(arrayConcat(hash, ciphertext));
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
    public String getIdentifier(@NonNull String reversiblePseudonym) {

        byte[] decoded = decoder.decode(reversiblePseudonym.substring(PREFIX.length()));
        byte[] iv = extractIv(decoded);
        byte[] cryptoText = Arrays.copyOfRange(decoded, PSEUDONYM_SIZE_BYTES, decoded.length);

        Cipher cipher = getCipherInstance();
        cipher.init(Cipher.DECRYPT_MODE, getEncryptionKey(), new IvParameterSpec(iv));

        byte[] plain = cipher.doFinal(cryptoText);
        return new String(plain, StandardCharsets.UTF_8);
    }



    @Override
    public String reverseAllContainedKeyedPseudonym(String containsKeyedPseudonyms) {
        return KEYED_PSEUDONYM_PATTERN.matcher(containsKeyedPseudonyms).replaceAll(m -> {
            String keyedPseudonym = m.group();
            //q: if this fails, just return 'm.group()' as-is?? to consider possibility that pattern matched
            // something it shouldn't
            return getIdentifier(keyedPseudonym);
        });
    }

}
