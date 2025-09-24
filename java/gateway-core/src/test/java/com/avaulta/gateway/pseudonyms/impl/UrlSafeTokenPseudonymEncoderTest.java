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

import java.util.Base64;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

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
    @SneakyThrows
    void reverseAll(String template) {
        String original = "blah";
        String encodedPseudonym =
            pseudonymEncoder.encode(Pseudonym.builder()
                    .hash(deterministicTokenizationStrategy.getToken(original, Function.identity()))
                .reversible(pseudonymizationStrategy.getReversibleToken(original, Function.identity())).build());

        String r = pseudonymEncoder.decodeAndReverseAllContainedKeyedPseudonyms(String.format(template, encodedPseudonym, encodedPseudonym),
            pseudonymizationStrategy);

        assertEquals(String.format(template, original, original), r);
    }

    @Test
    void noReverse() {
        String expected = "t~nVPSMYD7ZO_ptGIMJ65TAFo5_vVVQQ2af5Bfg7bW0Jo";
        String original = "blah";
        Pseudonym pseudonym = Pseudonym.builder()
            .hash(deterministicTokenizationStrategy.getToken(original, Function.identity()))
            .build();

        assertEquals(deterministicTokenizationStrategy.getTokenLength(),
            pseudonym.getHash().length);

        String encoded = pseudonymEncoder.encode(pseudonym);

        // 2 char prefix; plus each char of base64 is 6 bits of binary.
        // so 32 byte hash needs 43 chars of base64 to encode it
        assertEquals(45, encoded.length());
        assertEquals(expected, encoded);
        assertArrayEquals(deterministicTokenizationStrategy.getToken(original, Function.identity()), pseudonym.getHash());


        Pseudonym decoded = pseudonymEncoder.decode(encoded);
        assertArrayEquals(decoded.getHash(), pseudonym.getHash());
        assertArrayEquals(decoded.getReversible(), pseudonym.getReversible());
    }

    @Test
    void email() {
        String original = "alice@acme.com";
        Pseudonym pseudonym = Pseudonym.builder()
            .hash(deterministicTokenizationStrategy.getToken(original, Function.identity()))
            .domain("acme.com")
            .build();

        String encoded = pseudonymEncoder.encode(pseudonym);
        assertEquals("t~UFdK0TvVTvZ23c6QslyCy0o2MSq2DRtDjEXfTPJyyMk@acme.com", encoded);

        Pseudonym decoded = pseudonymEncoder.decode(encoded);
        assertEquals(new String(pseudonym.getHash()), new String(decoded.getHash()));
        assertEquals("acme.com", decoded.getDomain());
    }

    @Test
    void hashesMatch() {
        byte[] pseudonym = deterministicTokenizationStrategy.getToken("original", Function.identity());
        byte[] reversible = pseudonymizationStrategy.getReversibleToken("original", Function.identity());

        Pseudonym p = Pseudonym.builder()
            .hash(pseudonym)
            .build();

        Pseudonym r = Pseudonym.builder()
            .hash(pseudonym)
            .reversible(reversible)
            .build();

        // don't confuse with encoder under test; this is to provide readable comparison
        Base64.Encoder forComparison = Base64.getUrlEncoder().withoutPadding();

        assertEquals(
            forComparison.encodeToString(pseudonymEncoder.decode(pseudonymEncoder.encode(p)).getHash()),
            forComparison.encodeToString(pseudonymEncoder.decode(pseudonymEncoder.encode(r)).getHash())
        );

    }

    /**
     * test to reproduce exception we've seen in logs, just to establish what scenario leads to it
     */
    @Test
    void decoder2eException() {
        try {
            pseudonymEncoder.decode("t~UFdK0TvVTvZ23c6QslyCy0o2MSq2DRtDjEXfTPJyyMkacme.com");
            fail("expected exception");
        } catch (IllegalArgumentException e) {
            // 2e is the . in the domain, which wasn't stripped off bc missing '@'
            assertEquals("Illegal base64 character 2e", e.getMessage());
        }

    }
}
