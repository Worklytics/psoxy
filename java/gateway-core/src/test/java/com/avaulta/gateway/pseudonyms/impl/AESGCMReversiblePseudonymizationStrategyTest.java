package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.DeterministicPseudonymStrategy;
import com.avaulta.gateway.pseudonyms.Pseudonym;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class AESGCMReversiblePseudonymizationStrategyTest {

    AESGCMReversiblePseudonymStrategy pseudonymizationStrategy;
    DeterministicPseudonymStrategy deterministicPseudonymStrategy;

    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();

    @SneakyThrows
    @BeforeEach
    void setUp() {
        deterministicPseudonymStrategy = new Sha256DeterministicPseudonymStrategy("salt");
        pseudonymizationStrategy = new AESGCMReversiblePseudonymStrategy(
            deterministicPseudonymStrategy, TestUtils.testKey());
    }

    @Test
    void roundtrip() {

        byte[] pseudonym = pseudonymizationStrategy.getReversiblePseudonym("blah", Function.identity());
        assertNotEquals("blah".getBytes(), pseudonym);

        //something else shouldn't match
        byte[] pseudonym2 = pseudonymizationStrategy.getReversiblePseudonym("blah2", Function.identity());
        assertNotEquals(pseudonym2, pseudonym);

        String decrypted = pseudonymizationStrategy.getIdentifier(pseudonym);
        assertEquals("blah", decrypted);
    }

    @Test
    void reverse() {
        //given 'secret' and 'salt' the same, should be able to decrypt
        // (eg, our key-generation isn't random and nothing has any randomized state persisted
        //  somehow between tests)

       // String s = new String(encoder.encode(pseudonymizationStrategy.getKeyedPseudonym("blah",Function.identity())));

        assertEquals("blah",
            pseudonymizationStrategy.getIdentifier(decoder.decode("nVPSMYD7ZO_ptGIMJ65TAFo5_vVVQQ2af5Bfg7bW0JpFszshi2nfr3BovVcPFYct2qRdcA")));
    }


    @Test
    void pseudonymAsKeyPrefix() {

        byte[] keyed = pseudonymizationStrategy.getReversiblePseudonym("blah", Function.identity());
        byte[] pseudonym = deterministicPseudonymStrategy.getPseudonym("blah", Function.identity());

        assertTrue(Arrays.equals(Arrays.copyOfRange(keyed, 0, pseudonym.length), pseudonym),
            "pseudonym is prefix of keyed");
    }


    @Test
    void keyedPseudonym_sizes() {
        Random random = new Random();
        IntStream.generate(() -> random.nextInt(1000000000)).limit(100).forEach(i -> {
            String pseudonym = new String(encoder.encode(pseudonymizationStrategy.getReversiblePseudonym("blah" + i, Function.identity())));
            assertTrue(
                //q: why variable length for constant length input?
                // seem to range from 79 --> 82 bytes
                pseudonym.length() < Pseudonym.HASH_SIZE_BYTES + 52 //hash + cipher
            );
        });
    }





}
