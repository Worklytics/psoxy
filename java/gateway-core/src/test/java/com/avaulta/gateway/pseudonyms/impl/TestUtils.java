package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;

import javax.crypto.spec.SecretKeySpec;

public class TestUtils {


    public static SecretKeySpec testKey() {
        return AESReversibleTokenizationStrategy.aesKeyFromPassword("secret", "salt");
    }
}
