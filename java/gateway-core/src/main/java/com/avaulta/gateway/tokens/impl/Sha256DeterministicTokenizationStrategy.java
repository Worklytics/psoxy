package com.avaulta.gateway.tokens.impl;

import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.function.Function;


@RequiredArgsConstructor
public class Sha256DeterministicTokenizationStrategy implements DeterministicTokenizationStrategy {


    public static final int HASH_SIZE_BYTES = 32; //SHA-256

    @Getter
    final String salt;

    @Override
    public int getTokenLength() {
        return HASH_SIZE_BYTES;
    }

    @Override
    public byte[] getToken(String identifier, Function<String, String> canonicalization) {
        //pass in a canonicalization function? if not, this won't match for the canonically-equivalent
        // identifier in different formats (eg, cased/etc)

        // if pseudonyms too long, could cut this to MD5 (save 16 bytes) or SHA1 (save 12 bytes)
        // for our implementation, that should still be good enough
        return DigestUtils.sha256(canonicalization.apply(identifier) + getSalt());
    }

}
