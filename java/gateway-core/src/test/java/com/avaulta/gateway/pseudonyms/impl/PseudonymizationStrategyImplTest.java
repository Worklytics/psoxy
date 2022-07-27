package com.avaulta.gateway.pseudonyms.impl;

import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
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
            pseudonymizationStrategy.getIdentifier(PseudonymizationStrategyImpl.PREFIX + "NHXWS5CZDysDs3ETExXiMZxM2DfffirkjgmA64R9hCenHbNbPsOt4W-Hx8SDUaQY"));
    }


    @Test
    void pseudonymAsKeyPrefix() {

        String keyed = pseudonymizationStrategy.getKeyedPseudonym("blah", Function.identity());
        String pseudonym = pseudonymizationStrategy.getPseudonym("blah", Function.identity());


        byte[] keyedBytes = Base64.getUrlDecoder().decode(keyed.substring(PseudonymizationStrategyImpl.PREFIX.length()));
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
                PseudonymizationStrategyImpl.PREFIX.length() +
                    PseudonymizationStrategyImpl.PSEUDONYM_SIZE_BYTES * 2, //hash + ciphertext + prefix
                pseudonym.length());
        });
    }


    @Test
    void pseudonym_sizes() {

        //test a bunch of a values at random
        int randomSamples = 100;

        IntStream.range(0, randomSamples).forEach(i -> {
            //use a random UUID, to give original of typical length
            String original = UUID.randomUUID().toString();
            String pseudonym = pseudonymizationStrategy.getPseudonym(original, Function.identity());

            //43 bytes when base64-encoded without padding
            assertEquals(PseudonymizationStrategyImpl.KEYED_PSEUDONYM_LENGTH_WITHOUT_PREFIX, pseudonym.length());

            //32 bytes unencoded
            assertEquals(PseudonymizationStrategyImpl.PSEUDONYM_SIZE_BYTES,
                Base64.getUrlDecoder().decode(pseudonym).length);
        });
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
        String pseudonym = pseudonymizationStrategy.getKeyedPseudonym("blah", Function.identity());

        String r = pseudonymizationStrategy.reverseAllContainedKeyedPseudonyms(String.format(template, pseudonym, pseudonym));

        assertEquals(String.format(template, original, original), r);
    }

}
