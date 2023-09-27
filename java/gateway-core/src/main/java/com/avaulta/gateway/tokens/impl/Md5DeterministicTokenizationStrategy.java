package com.avaulta.gateway.tokens.impl;

import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.codec.digest.DigestUtils;


@RequiredArgsConstructor
public class Md5DeterministicTokenizationStrategy implements DeterministicTokenizationStrategy {

    public static final int HASH_LENGTH_BYTES = 16; //128 bits

    @Getter
    final String salt;

    @SneakyThrows
    @Override
    public byte[] getToken(String originalDatum) {
        return DigestUtils.md5(salt + originalDatum);
    }

    @Override
    public int getTokenLength() {
        return HASH_LENGTH_BYTES;
    }
}
