package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.AESReversibleTokenizationStrategy;
import com.avaulta.gateway.tokens.impl.Sha256DeterministicTokenizationStrategy;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class UrlSafeTokenPseudonymEncoderTest {


    UrlSafeTokenPseudonymEncoder pseudonymEncoder = new UrlSafeTokenPseudonymEncoder();

    ReversibleTokenizationStrategy pseudonymizationStrategy;

    DeterministicTokenizationStrategy deterministicTokenizationStrategy;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        deterministicTokenizationStrategy = new Sha256DeterministicTokenizationStrategy("salt");
        pseudonymizationStrategy = AESReversibleTokenizationStrategy.builder()
            .cipherSuite(AESReversibleTokenizationStrategy.CBC)
            .key(TestUtils.testKey())
            .deterministicTokenizationStrategy(deterministicTokenizationStrategy)
            .build();
    }

    @Test
    void roundtrip() {
        String expected = "p~nVPSMYD7ZO_ptGIMJ65TAFo5_vVVQQ2af5Bfg7bW0Jq9JIOXfBWhts_zA5Ns0r4m";
        String original = "blah";
        Pseudonym pseudonym = Pseudonym.builder()
            .reversible(pseudonymizationStrategy.getReversibleToken(original, Function.identity()))
            .build();

        String encoded = pseudonymEncoder.encode(pseudonym);

        assertEquals(expected, encoded);
        assertArrayEquals(deterministicTokenizationStrategy.getToken(original, Function.identity()), pseudonym.getHash());


        Pseudonym decoded = pseudonymEncoder.decode(encoded);
        assertArrayEquals(decoded.getHash(), pseudonym.getHash());
        assertArrayEquals(decoded.getReversible(), pseudonym.getReversible());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://api.acme.com/v1/accounts/%s",
        "https://api.acme.com/v1/accounts/%s/calendar",
        "https://api.acme.com/v1/accounts/%s/calendar?param=blah&param2=blah2",
        "https://api.acme.com/v1/accounts?id=%s",
        "https://api.acme.com/v1/accounts/%s?id=%s", //doubles
        "https://api.acme.com/v1/accounts/%s?id=p~12adsfasdfasdf31",  //something else with prefix
        "https://api.acme.com/v1/accounts/p~12adsfasdfasdf31?id=%s", //something else with prefix, before actual value
        "https://api.acme.com/v1/accounts",
        "",
    })
    void reverseAll(String template) {
        String original = "blah";
        String encodedPseudonym =
            pseudonymEncoder.encode(Pseudonym.builder()
                .reversible(pseudonymizationStrategy.getReversibleToken(original, Function.identity())).build());

        String r = pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(String.format(template, encodedPseudonym, encodedPseudonym),
            pseudonymizationStrategy);

        assertEquals(String.format(template, original, original), r);
    }
}
