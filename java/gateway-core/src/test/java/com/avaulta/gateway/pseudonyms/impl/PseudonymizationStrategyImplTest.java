package com.avaulta.gateway.pseudonyms.impl;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PseudonymizationStrategyImplTest {

    PseudonymizationStrategyImpl pseudonymizationStrategy;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        pseudonymizationStrategy = new PseudonymizationStrategyImpl("salt", TestUtils.testKey());
    }


    @Test
    void roundtrip() {

        String pseudonym = pseudonymizationStrategy.getKeyedPseudonym("blah", Function.identity());
        assertNotEquals("blah", pseudonym);

        //something else shouldn't match
        String pseudonym2 = pseudonymizationStrategy.getKeyedPseudonym("blah2", Function.identity());
        assertNotEquals(pseudonym2, pseudonym);

        String decrypted = pseudonymizationStrategy.getIdentifier(pseudonym);
        assertEquals("blah", decrypted);
    }

    @Test
    void reverse() {
        //given 'secret' and 'salt' the same, should be able to decrypt
        // (eg, our key-generation isn't random and nothing has any randomized state persisted
        //  somehow between tests)

        assertEquals("blah",
            pseudonymizationStrategy.getIdentifier("NHXWS5CZDysDs3ETExXiMZxM2DfffirkjgmA64R9hCenHbNbPsOt4W-Hx8SDUaQY"));
    }


    @Test
    void pseudonymAsKeyPrefix() {

        String keyed = pseudonymizationStrategy.getKeyedPseudonym("blah", Function.identity());
        String pseudonym = pseudonymizationStrategy.getPseudonym("blah", Function.identity());


        byte[] keyedBytes = Base64.getUrlDecoder().decode(keyed);
        byte[] pseudonymBytes = Base64.getUrlDecoder().decode(pseudonym);

        assertTrue(Arrays.equals(Arrays.copyOfRange(keyedBytes, 0, pseudonymBytes.length), pseudonymBytes),
            "pseudonym is prefix of keyed");
    }


    @Test
    void keyedPseudonym_sizes() {
        Random random = new Random();
        IntStream.generate(() -> random.nextInt(1000000000)).limit(100).forEach(i -> {
            String pseudonym = pseudonymizationStrategy.getKeyedPseudonym("blah" + i, Function.identity());
            assertEquals(
                PseudonymizationStrategyImpl.PSEUDONYM_SIZE_BYTES * 2, //hash + ciphertext
                pseudonym.length());
        });
    }


    @Test
    void pseudonym_sizes() {

        Random random = new Random();
        IntStream.generate(() -> random.nextInt(1000000000)).limit(100).forEach(i -> {
            String pseudonym = pseudonymizationStrategy.getPseudonym("blah" + i, Function.identity());

            //43 bytes when base64-encoded without padding
            assertEquals(43, pseudonym.length());

            //32 bytes unencoded
            assertEquals(PseudonymizationStrategyImpl.PSEUDONYM_SIZE_BYTES,
                Base64.getUrlDecoder().decode(pseudonym).length);
        });
    }
}
