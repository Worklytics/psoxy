package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import lombok.SneakyThrows;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.KeySpec;

public class TestUtils {


    public static SecretKeySpec testKey() {
        return AESReversibleTokenizationStrategy.aesKeyFromPassword("secret", "salt");
    }
}
