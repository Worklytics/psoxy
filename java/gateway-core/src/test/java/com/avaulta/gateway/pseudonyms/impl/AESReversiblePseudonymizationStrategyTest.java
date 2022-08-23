package com.avaulta.gateway.pseudonyms.impl;

import com.avaulta.gateway.pseudonyms.DeterministicPseudonymStrategy;
import com.avaulta.gateway.pseudonyms.ReversiblePseudonymStrategy;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AESReversiblePseudonymizationStrategyTest {

    static DeterministicPseudonymStrategy deterministicPseudonymStrategy;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        deterministicPseudonymStrategy = new Sha256DeterministicPseudonymStrategy("salt");
    }

    static Stream<Arguments> getStrategies() {
        deterministicPseudonymStrategy = new Sha256DeterministicPseudonymStrategy("salt");
        return Stream.of(
            Arguments.of(
                AESReversiblePseudonymStrategy.builder()
                    .cipherSuite(AESReversiblePseudonymStrategy.GCM)
                    .key(TestUtils.testKey())
                    .deterministicPseudonymStrategy(deterministicPseudonymStrategy)
                    .build()
            ),
            Arguments.of(
                AESReversiblePseudonymStrategy.builder()
                    .cipherSuite(AESReversiblePseudonymStrategy.CBC)
                .key(TestUtils.testKey())
                .deterministicPseudonymStrategy(deterministicPseudonymStrategy)
                .build()
            )
        );
    }

    @MethodSource("getStrategies")
    @ParameterizedTest
    void roundtrip(ReversiblePseudonymStrategy reversiblePseudonymStrategy) {

        byte[] pseudonym = reversiblePseudonymStrategy.getReversiblePseudonym("blah", Function.identity());
        assertNotEquals("blah".getBytes(), pseudonym);

        //something else shouldn't match
        byte[] pseudonym2 = reversiblePseudonymStrategy.getReversiblePseudonym("blah2", Function.identity());
        assertNotEquals(pseudonym2, pseudonym);

        String decrypted = reversiblePseudonymStrategy.getIdentifier(pseudonym);
        assertEquals("blah", decrypted);
    }
    @MethodSource("getStrategies")
    @ParameterizedTest
    void pseudonymAsKeyPrefix(ReversiblePseudonymStrategy reversiblePseudonymStrategy) {

        byte[] keyed = reversiblePseudonymStrategy.getReversiblePseudonym("blah", Function.identity());
        byte[] pseudonym = deterministicPseudonymStrategy.getPseudonym("blah", Function.identity());

        assertTrue(Arrays.equals(Arrays.copyOfRange(keyed, 0, pseudonym.length), pseudonym),
            "pseudonym is prefix of keyed");
    }
}
