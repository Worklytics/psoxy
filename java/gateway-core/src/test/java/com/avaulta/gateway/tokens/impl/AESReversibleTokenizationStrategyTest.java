package com.avaulta.gateway.tokens.impl;

import com.avaulta.gateway.pseudonyms.impl.TestUtils;
import com.avaulta.gateway.tokens.DeterministicTokenizationStrategy;
import com.avaulta.gateway.tokens.ReversibleTokenizationStrategy;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AESReversibleTokenizationStrategyTest {

    static DeterministicTokenizationStrategy deterministicTokenizationStrategy;

    @SneakyThrows
    @BeforeEach
    void setUp() {
        deterministicTokenizationStrategy = new Sha256DeterministicTokenizationStrategy("salt");
    }

    static Stream<Arguments> getStrategies() {
        deterministicTokenizationStrategy = new Sha256DeterministicTokenizationStrategy("salt");
        return Stream.of(
            Arguments.of(
                AESReversibleTokenizationStrategy.builder()
                    .cipherSuite(AESReversibleTokenizationStrategy.GCM)
                    .key(TestUtils.testKey())
                    .deterministicTokenizationStrategy(deterministicTokenizationStrategy)
                    .build()
            ),
            Arguments.of(
                AESReversibleTokenizationStrategy.builder()
                    .cipherSuite(AESReversibleTokenizationStrategy.CBC)
                .key(TestUtils.testKey())
                .deterministicTokenizationStrategy(deterministicTokenizationStrategy)
                .build()
            )
        );
    }

    @MethodSource("getStrategies")
    @ParameterizedTest
    void roundtrip(ReversibleTokenizationStrategy reversibleTokenizationStrategy) {

        byte[] pseudonym = reversibleTokenizationStrategy.getReversibleToken("blah", Function.identity());
        assertNotEquals("blah".getBytes(), pseudonym);

        //something else shouldn't match
        byte[] pseudonym2 = reversibleTokenizationStrategy.getReversibleToken("blah2", Function.identity());
        assertNotEquals(pseudonym2, pseudonym);

        String decrypted = reversibleTokenizationStrategy.getOriginalDatum(pseudonym);
        assertEquals("blah", decrypted);
    }
    @MethodSource("getStrategies")
    @ParameterizedTest
    void pseudonymAsKeyPrefix(ReversibleTokenizationStrategy reversibleTokenizationStrategy) {

        byte[] keyed = reversibleTokenizationStrategy.getReversibleToken("blah", Function.identity());
        byte[] pseudonym = deterministicTokenizationStrategy.getToken("blah", Function.identity());

        assertTrue(Arrays.equals(Arrays.copyOfRange(keyed, 0, pseudonym.length), pseudonym),
            "pseudonym is prefix of keyed");
    }
}
