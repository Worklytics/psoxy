package com.avaulta.gateway.tokens.impl;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class Md5DeterministicTokenizationStrategyTest {

    Md5DeterministicTokenizationStrategy md5DeterministicTokenizationStrategy =
        new Md5DeterministicTokenizationStrategy("salt");
    @Test
    void getToken() {

        byte[] hash = md5DeterministicTokenizationStrategy.getToken("blah");

        assertEquals(md5DeterministicTokenizationStrategy.getTokenLength(), hash.length);
        assertEquals("xWuyGM9CqnoIqtcBLTP2uQ", Base64.getEncoder().withoutPadding().encodeToString(hash));
    }


    @Test
    void getToken_canonicalization() {

        byte[] hash = md5DeterministicTokenizationStrategy.getToken("blah", String::toLowerCase);
        byte[] hashUpper = md5DeterministicTokenizationStrategy.getToken("BLAH", String::toLowerCase);
        byte[] hashMixed = md5DeterministicTokenizationStrategy.getToken("BlAh", String::toLowerCase);

        assertEquals(md5DeterministicTokenizationStrategy.getTokenLength(), hash.length);
        final String EXPECTED = "xWuyGM9CqnoIqtcBLTP2uQ";
        assertEquals(EXPECTED, Base64.getEncoder().withoutPadding().encodeToString(hash));
        assertEquals(EXPECTED, Base64.getEncoder().withoutPadding().encodeToString(hashUpper));
        assertEquals(EXPECTED, Base64.getEncoder().withoutPadding().encodeToString(hashMixed));
    }

    @Test
    void getToken_salted() {

        final String VALUE = "blah";
        Md5DeterministicTokenizationStrategy withDifferentSalt = new Md5DeterministicTokenizationStrategy("different salt");
        assertNotEquals(
            Base64.getEncoder().encodeToString(md5DeterministicTokenizationStrategy.getToken(VALUE)),
            Base64.getEncoder().encodeToString(withDifferentSalt.getToken(VALUE))
        );
    }
}
