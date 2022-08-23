package com.avaulta.gateway.pseudonyms.impl;

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

class AESGCMPseudonymizationStrategyTest {

    AESGCMPseudonymizationStrategy pseudonymizationStrategy;

    //base64url-encoding without padding
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    Base64.Decoder decoder = Base64.getUrlDecoder();

    @SneakyThrows
    @BeforeEach
    void setUp() {
        pseudonymizationStrategy = new AESGCMPseudonymizationStrategy("salt", TestUtils.testKey());
    }

    @Test
    void roundtrip() {

        byte[] pseudonym = pseudonymizationStrategy.getKeyedPseudonym("blah", Function.identity());
        assertNotEquals("blah".getBytes(), pseudonym);

        //something else shouldn't match
        byte[] pseudonym2 = pseudonymizationStrategy.getKeyedPseudonym("blah2", Function.identity());
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

        byte[] keyed = pseudonymizationStrategy.getKeyedPseudonym("blah", Function.identity());
        byte[] pseudonym = pseudonymizationStrategy.getPseudonym("blah", Function.identity());

        assertTrue(Arrays.equals(Arrays.copyOfRange(keyed, 0, pseudonym.length), pseudonym),
            "pseudonym is prefix of keyed");
    }


    @Test
    void keyedPseudonym_sizes() {
        Random random = new Random();
        IntStream.generate(() -> random.nextInt(1000000000)).limit(100).forEach(i -> {
            String pseudonym = new String(encoder.encode(pseudonymizationStrategy.getKeyedPseudonym("blah" + i, Function.identity())));
            assertTrue(
                //q: why variable length for constant length input?
                // seem to range from 79 --> 82 bytes
                pseudonym.length() < Pseudonym.HASH_SIZE_BYTES + 52 //hash + cipher
            );
        });
    }


    @Test
    void pseudonym_sizes() {

        //test a bunch of a values at random
        int randomSamples = 100;

        IntStream.range(0, randomSamples).forEach(i -> {
            //use a random UUID, to give original of typical length
            String original = UUID.randomUUID().toString();
            byte[] pseudonym = pseudonymizationStrategy.getPseudonym(original, Function.identity());

            //43 bytes when base64-encoded without padding
            assertEquals(43, new String(encoder.encode(pseudonym)).length());

            //32 bytes unencoded
            assertEquals(Pseudonym.HASH_SIZE_BYTES, pseudonym.length);
        });
    }


}
