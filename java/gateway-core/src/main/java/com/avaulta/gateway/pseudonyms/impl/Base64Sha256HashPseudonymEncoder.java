package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.pseudonyms.PseudonymEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * implementation of defacto encoding used by BulkDataSanitizerImpl as of v0.4.30 when
 * configured with PSEUDONYM_FORMAT=URL_SAFE_TOKEN and PSEUDONYM_IMPLEMENTATION=default
 *
 * eg, `Pseudonym` encoded as the base64-url-safe encoding of its SHA-256 hash, with no prefix
 *
 */
public class Base64Sha256HashPseudonymEncoder implements PseudonymEncoder {


    @Override
    public String encode(Pseudonym pseudonym) {
        return base64Encode(pseudonym.getHash());
    }

    @Override
    public Pseudonym decode(String input) {
        if (!canBeDecoded(input)) {
            throw new IllegalArgumentException("input cannot be decoded");
        }

        return Pseudonym.builder().hash(base64decode(input)).build();
    }

    @Override
    public boolean canBeDecoded(String possiblePseudonym) {
        return possiblePseudonym != null &&
            //NOTE: this couples it to the SHA-256 hash; otherwise has nothing specific to SHA-256
            // - and even this is only based on the expected length in bytes of SHA-256 hash
            possiblePseudonym.getBytes(StandardCharsets.UTF_8).length == 43; //43 rather than 32, bc of base64 encoding without padding
    }

    //base64 encoding, to match implementation in HashUtils.java from psoxy-core v0.4.30
    String base64Encode(byte[] bytes) {
        return new String(Base64.getUrlEncoder()
                .withoutPadding()
                .encode(bytes));
    }

    byte[] base64decode(String input) {
        return Base64.getUrlDecoder().decode(input);
    }
}
