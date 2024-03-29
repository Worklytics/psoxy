package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class Base64UrlSha256HashPseudonymEncoderTest {

    Base64UrlSha256HashPseudonymEncoder encoder = new Base64UrlSha256HashPseudonymEncoder();



    @ParameterizedTest
    @ValueSource(strings = {
        "szTDtLRsbo-JneQPAYEYN7g5hjcvfttONtzUv5hFWZo",
    })
    void canBeDecoded(String encoded) {
        assertTrue(encoder.canBeDecoded(encoded));

        //just ensure doesn't throw anything
        encoder.decode(encoded);
    }


    @ParameterizedTest
    @ValueSource(strings = {
        "asdfasdf",
        "1343287afdaskdljf4324sasdfa",
    })
    void cannotBeDecoded(String encoded) {
        assertFalse(encoder.canBeDecoded(encoded));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "asdfasdf",
        "1343287afdaskdljf4324sasdfa",
        "asdf1234234",
        "alice@acme.com"
    })
    void roundtrip(String identifier) {
        Sha256DeterministicTokenizationStrategy sha256DeterministicTokenizationStrategy =
            new Sha256DeterministicTokenizationStrategy("salt");

        Pseudonym pseudonym = Pseudonym.builder()
            .hash(sha256DeterministicTokenizationStrategy.getToken(identifier, Function.identity()))
            .build();

        String encoded = encoder.encode(pseudonym);

        assertTrue(encoder.canBeDecoded(encoded));
        assertEquals(new String(pseudonym.getHash()),
            new String(encoder.decode(encoded).getHash()));
    }

}
